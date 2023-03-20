/*
 * 
 *  Hive Receiver Heating Driver
 *	
 */


@Field String driverVersion = "v0.60 (21st March 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field String deviceName = "Hive Receiver Heating"
@Field boolean debugMode = true
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 4
@Field int receiverEndpoint = 5

metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hive/drivers/hive_receiver_heating.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "PresenceSensor"
		capability "Refresh"
		capability "TemperatureMeasurement"
		capability "Thermostat"
		capability "ThermostatHeatingSetpoint"
		capability "ThermostatMode"
		capability "ThermostatOperatingState"
		capability "ThermostatSetpoint"

		attribute "heatingBoostRemaining", "number"
		attribute "heatingMode", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		command "getSystemMode"

		fingerprint profileId: "0104", inClusters: "0000,0003,0009,000A,0201,FD00", outClusters: "000A,0402,0019", manufacturer: "Computime", model: "SLR2", deviceJoinName: "Computime Boiler Controller SLR2"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")

}


void configureSpecifics() {
	// Called by general configure() method

	device.name = "${getParent().device.name} Heating"
	device.label = "${getParent().device.label} (Heating)"
	refresh()

}


void updateSpecifics() {
	// Called by library updated() method

	return

}


void refresh() {

	getOccupiedHeatingSetpoint()
	logging("${device} : Refreshed", "info")

}


void auto() {
	
	getParent().auto(receiverEndpoint)

}


void cool() {

	getParent().off(receiverEndpoint)

}


void setCoolingSetpoint(BigDecimal temperature) {

	getParent().setCoolingSetpoint(receiverEndpoint, temperature)

}

void emergencyHeat() {

	getParent().emergencyHeat(receiverEndpoint)

}


void fanAuto() {

	getParent().fanAuto(receiverEndpoint)

}


void fanCirculate() {

	getParent().fanCirculate(receiverEndpoint)

}


void fanOn() {

	getParent().fanOn(receiverEndpoint)

}


void heat() {

	getParent().heat(receiverEndpoint)

}


void setHeatingSetpoint(BigDecimal temperature) {

	getParent().setHeatingSetpoint(receiverEndpoint, temperature)

}


void setThermostatMode(String thermostatMode) {

	getParent().setThermostatMode(receiverEndpoint, thermostatMode)
	
}


void setThermostatFanMode(String fanMode) {

	getParent().setThermostatFanMode(receiverEndpoint, fanMode)
	
}


void off() {

	getParent().off(receiverEndpoint)

}


void getOccupiedHeatingSetpoint() {

	getParent().getOccupiedHeatingSetpoint(receiverEndpoint)

}


void getSystemMode() {

	getParent().getSystemMode(receiverEndpoint)

}


void getTemperatureSetpointHold() {

	getParent().getTemperatureSetpointHold(receiverEndpoint)

}


void getTemperatureSetpointHoldDuration() {

	getParent().getTemperatureSetpointHoldDuration(receiverEndpoint)

}


void getThermostatRunningState() {

	getParent().getThermostatRunningState(receiverEndpoint)

}


void processMap(Map map) {

	updatePresence()
	checkDriver()

	if (map.cluster == "0201") {
		// Thermostat Cluster

		if (map.attrId == "0000" || map.attrId == "0012") {
			// Temperature or OccupiedHeatingSetpoint

			String temperatureType = ("${map.attrId}" == "0000") ? "temperature" : "heatingSetpoint"

			BigDecimal temperature = hexStrToSignedInt(map.value)
			temperature = temperature / 100
			temperature = temperature.setScale(1, BigDecimal.ROUND_DOWN)  // They seem to round down for the stat display.

			logging("${device} : ${temperatureType} : ${temperature} from hex value ${map.value} ", "debug")

			String temperatureScale = location.temperatureScale
			if (temperatureScale == "F") {
				temperature = (temperature * 1.8) + 32
			}

			logging("${device} : ${temperatureType} : ${temperature} °${temperatureScale}", "info")
			sendEvent(name: "${temperatureType}", value: temperature, unit: "${temperatureScale}")

			if (temperatureType == "heatingSetpoint") {
				// We need to check whether this was a scheduled or manual setpoint change.

				getSystemMode()
				getTemperatureSetpointHoldDuration()

			}

		} else if (map.attrId == "001C") {

			// Received mode data.
			logging("${device} : mode : ${map.value} on endpoint ${map.endpoint}", "debug")

			String channel = ("${map.endpoint}" == "05") ? "heating" : "water"

			switch(map.value) {

				case "00":
					// This always represents off.
					logging("${device} : mode : ${channel} off", "debug")
					sendEvent(name: "thermostatMode", value: "off")
					sendEvent(name: "heatingMode", value: "off")
					getThermostatRunningState()
					break
				case "04":
					// This always represents normal heating, but we must request the hold state to know if we're scheduled or manual.
					logging("${device} : mode : ${channel} on - requesting hold state", "debug")
					getTemperatureSetpointHold()
					break
				case "05":
					// This always represents boost mode.
					logging("${device} : mode : ${channel} boost", "debug")
					sendEvent(name: "thermostatMode", value: "emergency heat")
					sendEvent(name: "heatingMode", value: "boost")
					break
				default:
					logging("${device} : mode : unknown ${channel} mode received", "warn")
					break

			}

		} else if (map.attrId == "0023") {
			// TemperatureSetpointHold

			logging("${device} : setpoint hold : ${map.value} on endpoint ${map.endpoint}", "debug")

			String channel = ("${map.endpoint}" == "05") ? "thermostat" : "waterstat"

			switch(map.value) {

				case "00":
					// This always represents scheduled mode as the hold is off, so the schedule is running.
					sendEvent(name: "${channel}Mode", value: "auto")
					sendEvent(name: "heatingMode", value: "schedule")
					logging("${device} : setpoint hold : ${map.value} on endpoint ${map.endpoint}", "debug")
					break
				case "01":
					// The hold is on, so the schedule is paused. We're in some sort of manual mode.
					//   NOTE! If the system was in schedule (auto) mode and the setpoint altered without changing from that mode,
					//         the system switches to a hybrid mode where the altered setpoint runs for the duration until the
					//         next programme change in the schedule. At that point the programme resumes, but the thermostat
					//         always reports "SCH" despite being in a manual state.
					sendEvent(name: "${channel}Mode", value: "heat")
					sendEvent(name: "heatingMode", value: "manual")
					break

			}

		} else if (map.attrId == "0024") {
			// TemperatureSetpointHoldDuration

			String channel = ("${map.endpoint}" == "05") ? "heatingBoostRemaining" : "waterBoostRemaining"
			BigDecimal holdDuration = hexStrToSignedInt(map.value)
			(holdDuration < 0) ? holdDuration = 0 : holdDuration

			logging("${device} : ${channel} : ${holdDuration} (${map.value}) on endpoint ${map.endpoint}", "debug")

			sendEvent(name: "${channel}", value: holdDuration)

			// If we're not in emergency heat (boost) mode then a temporary override is happening.
			String currentSystemMode = device.currentState("thermostatMode").value
			if (currentSystemMode == "heat" && holdDuration > 0) sendEvent(name: "heatingMode", value: "override")

		} else if (map.attrId == "0029") {
			// ThermostatRunningState

			String channel = ("${map.endpoint}" == "05") ? "thermostatOperatingState" : "waterstatOperatingState"

			logging("${device} : ${channel} : ${map.value} on endpoint ${map.endpoint}", "debug")

			switch(map.value) {

				case "0000":
					sendEvent(name: "${channel}", value: "idle")
					logging("${device} : ${channel} : idle", "debug")
					break
				case "0001":
					sendEvent(name: "${channel}", value: "heating")
					logging("${device} : ${channel} : heating", "debug")
					break
				default:
					logging("${device} : ${channel} : Received an unknown boiler control state!", "warn")
					break

			}

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}
	
}
