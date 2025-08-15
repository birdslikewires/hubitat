/*
 * 
 *  Hive Receiver Heating Driver
 *	
 */


@Field String driverVersion = "v0.70 (6th September 2023)"
@Field boolean debugMode = false


#include BirdsLikeWires.library
import groovy.json.JsonOutput
import groovy.transform.Field

@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 4
@Field int receiverEndpoint = 5

metadata {

	definition (name: "Hive Receiver Heating", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/hive/drivers/hive_receiver_heating.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "Refresh"
        capability "Sensor"
		capability "TemperatureMeasurement"
		capability "Thermostat"
		capability "ThermostatHeatingSetpoint"
		capability "ThermostatMode"
		capability "ThermostatOperatingState"
		capability "ThermostatSetpoint"

		attribute "overrideMinutes", "number"
		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

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

	// Set expected modes and values.
	sendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(["auto", "emergency heat", "heat", "off"]))
	sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["auto"]))
	sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: false)

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

	getParent().cool(receiverEndpoint)

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

	updateHealthStatus()
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

				// System is heating-only. The cooling setpoint can only ever be our heating target.
				sendEvent(name: "coolingSetpoint", value: temperature, unit: "${temperatureScale}")
				sendEvent(name: "thermostatSetpoint", value: temperature, unit: "${temperatureScale}")

				// Now we need to check whether this was a scheduled or manual setpoint change.
				getSystemMode()
				getTemperatureSetpointHoldDuration()

			}

		} else if (map.attrId == "001C") {

			// Received mode data.
			logging("${device} : mode : ${map.value} on endpoint ${map.endpoint}", "debug")

			switch(map.value) {

				case "00":
					// This always represents off.
					logging("${device} : mode : heating off", "debug")
					sendEvent(name: "thermostatMode", value: "off")
					getThermostatRunningState()
					break
				case "04":
					// This always represents normal heating, but we must request the hold state to know if we're scheduled or manual.
					logging("${device} : mode : heating on - requesting hold state", "debug")
					getTemperatureSetpointHold()
					break
				case "05":
					// This always represents boost mode.
					logging("${device} : mode : heating boost", "debug")
					sendEvent(name: "thermostatMode", value: "emergency heat")
					break
				default:
					logging("${device} : mode : unknown heating mode received", "warn")
					break

			}

		} else if (map.attrId == "0023") {
			// TemperatureSetpointHold

			logging("${device} : setpoint hold : ${map.value} on endpoint ${map.endpoint}", "debug")

			switch(map.value) {

				case "00":
					// This always represents scheduled mode as the hold is off, so the schedule is running.
					sendEvent(name: "thermostatMode", value: "auto")
					logging("${device} : setpoint hold : ${map.value} on endpoint ${map.endpoint}", "debug")
					break
				case "01":
					// The hold is on, so the schedule is paused. We're in some sort of manual mode.
					//   NOTE! If the system was in schedule (auto) mode and the setpoint altered without changing from that mode,
					//         the system switches to a hybrid mode where the altered setpoint runs for the duration until the
					//         next programme change in the schedule. At that point the programme resumes, but the thermostat
					//         always reports "SCH" despite being in a manual state.
					sendEvent(name: "thermostatMode", value: "heat")
					break

			}

		} else if (map.attrId == "0024") {
			// TemperatureSetpointHoldDuration

			BigDecimal holdDuration = hexStrToSignedInt(map.value)
			(holdDuration < 0) ? holdDuration = 0 : holdDuration

			logging("${device} : overrideMinutes : ${holdDuration} (${map.value}) on endpoint ${map.endpoint}", "debug")

			sendEvent(name: "overrideMinutes", value: holdDuration)

		} else if (map.attrId == "0029") {
			// ThermostatRunningState

			logging("${device} : thermostatOperatingState : ${map.value} on endpoint ${map.endpoint}", "debug")

			switch(map.value) {

				case "0000":
					sendEvent(name: "thermostatOperatingState", value: "idle")
					logging("${device} : thermostatOperatingState : idle", "debug")
					break
				case "0001":
					sendEvent(name: "thermostatOperatingState", value: "heating")
					logging("${device} : thermostatOperatingState : heating", "debug")
					break
				default:
					logging("${device} : thermostatOperatingState : Received an unknown boiler control state!", "warn")
					break

			}

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}
	
}
