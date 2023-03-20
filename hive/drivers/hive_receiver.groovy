/*
 * 
 *  Hive Receiver Heating Driver
 *	
 */


@Field String driverVersion = "v0.55 (14th March 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "Hive Receiver Heating", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hive/drivers/hive_receiver_heating.groovy") {

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

		command "getThermostatMode"

		fingerprint profileId: "0104", inClusters: "0000,0003,0009,000A,0201,FD00", outClusters: "000A,0402,0019", manufacturer: "Computime", model: "SLR2", deviceJoinName: "Computime Boiler Controller SLR2"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


def testCommand() {

	logging("${device} : Test Command", "info")

}


void configureSpecifics() {
	// Called by general configure() method

	String modelCheck = "${getDeviceDataByName('model')}"
	device.name = "Hive Receiver ${modelCheck}"
	setThermostatDateAndTime()

	// Reporting
	//  These had to be constructed manually as configureReporting seems to ignore the [destEndpoint:0x06] additional parameter.
	//  NOTE! Though the water (endpoint 0x06) bind is reported as successful no reports are ever sent. The endpoint needs to be polled. :(
	//        Attributes 1C, 23, 24 and 29 all apply to endpoint 6. Temperature setpoint (12) does not apply (set on the boiler).
	sendZigbeeCommands([
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0012 0x29 1 43200 {} {}, delay 2000",			// (0x0201, 0x0012) OccupiedHeatingSetpoint
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x001C 0x30 1 43200 {} {}, delay 2000",			// (0x0201, 0x001C) SystemMode 
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0023 0x30 1 43200 {} {}, delay 2000",			// (0x0201, 0x0023) TemperatureSetpointHold 
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0024 0x21 1 43200 {} {}, delay 2000",			// (0x0201, 0x0024) TemperatureSetpointHoldDuration
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0029 0x19 1 43200 {} {}, delay 2000"			// (0x0201, 0x0029) ThermostatRunningState
	])

	sendZigbeeCommands(zigbee.configureReporting(0x0201, 0x0000, 0x29, 1, 60))							// (0x0201, 0x0000) Temperature Reporting

}


void updateSpecifics() {
	// Called by library updated() method

	return

}


void refresh() {

	getThermostatMode()
	logging("${device} : Refreshed", "info")

}


void auto() {
	// Schedule mode. This will run any existing schedule set through the thermostat.

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04)	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x00)	// TemperatureSetpointHold
	sendZigbeeCommands(cmds)

	logging("${device} : System set to schedule mode.", "info")

	// runIn(3,getThermostatMode)

}


void cool() {
	// This is a heat-only system, so cooling and off are essentially the same thing. Open a window!

	logging("${device} : System switching off, there is no active cooling.", "info")
	off()

}


void emergencyHeat() {
	// Boost mode.

	int boostTime = 30
	int boostTemp = 2400

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x05, [destEndpoint: 0x05], 0)			// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01, [destEndpoint: 0x05], 0)			// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, boostTime, [destEndpoint: 0x05], 0)		// TemperatureSetpointHoldDuration
	cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, boostTemp, [destEndpoint: 0x05], 0) 	// OccupiedHeatingSetpoint
	sendZigbeeCommands(cmds)

	logging("${device} : System boosting to ${boostTemp} for ${boostTime} minutes.", "info")

	//runIn(3,getThermostatMode)

}


void fanAuto() {
	// No controllable fans here.

	logging("${device} : No controllable fans.", "info")
	return

}


void fanCirculate() {
	// No controllable fans here either.

	logging("${device} : No controllable fans.", "info")
	return

}


void fanOn() {
	// Still no controllable fans, stop asking.

	logging("${device} : No controllable fans.", "info")
	return

}


void heat() {
	// Manual mode.


	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04)				// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01)				// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0x0)				// TemperatureSetpointHoldDuration
	sendZigbeeCommands(cmds)

}


void setHeatingSetpoint(BigDecimal temperature) {

	if ("${device.currentState("thermostatMode").value}" == "off") {

		logging("${device} : Setpoint : System is turned off, this will have no effect.", "warn")

	}

	// Convert from degF.
	if ("${location.temperatureScale}" == "F") temperature = (temperature / 1.8) - 32
	
	(temperature < 5) ? temperature = 1 : temperature		// Anything lower than 5degC is frost protect mode.
	(temperature > 32) ? temperature = 32 : temperature		// Anything higher than 32degC is not supported.

	// System works in 0.5degC steps.
	temperature = temperature * 2
	temperature = temperature.setScale(0, BigDecimal.ROUND_HALF_UP)
	temperature = temperature / 2
	temperature = temperature.setScale(2, BigDecimal.ROUND_UP) * 100
	int temperatureInt = temperature.toInteger()

	logging("${device} : setHeatingSetpoint : sanitised temperature input to ${temperatureInt}", "debug")

	sendZigbeeCommands(zigbee.writeAttribute(0x0201, 0x0012, 0x29, temperatureInt))
	//state.lastHeatingSetpointRequest = temperatureInt / 100

}


void off() {
	// Turns everything off, but respects the frost protect setting.

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x00)	// SystemMode to off
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01)	// TemperatureSetpointHold 
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0xFFFF)	// TemperatureSetpointHoldDuration
	sendZigbeeCommands(cmds)

	//runIn(3,getThermostatMode)

}


void getHoldState() {
	// Request current system hold state.

	sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0023))

}


void getThermostatMode() {
	// Request current system mode.

	sendZigbeeCommands(zigbee.readAttribute(0x201, 0x001C))

}


void setThermostatMode(thermostatmode) {

	logging("${device} : setThermostatMode : ${thermostatmode} ", "debug")

	switch(thermostatmode) {

		case "auto":
			auto()
			break
		case "off":
			off()
			break
		case "heat":
			heat()
			break
		case "emergency heat":
			emergencyHeat()
			break
		case "cool":
			off()
			break

	}

}


def setThermostatDateAndTime() {

	// Zigbee epoch is measured from 1st January 2000 so we need to subtract 30 years worth of seconds from UNIX time!
	int zigbeeEpochTime = now()/1000-946684800

	String zigbeeHexTime = zigbee.convertToHexString(zigbeeEpochTime,8)
	String zigbeeHexTimeReversed =
		new StringBuilder(8)
			.append(zigbeeHexTime, 6, 8)
			.append(zigbeeHexTime, 4, 6)
			.append(zigbeeHexTime, 2, 4)
			.append(zigbeeHexTime, 0, 2)
			.toString();

	logging("${device} : zigbeeEpochTime = $zigbeeEpochTime | zigbeeHexTime = $zigbeeHexTime | zigbeeHexTimeReversed = $zigbeeHexTimeReversed", "debug")

	sendZigbeeCommands(["he wattr 0x${device.deviceNetworkId} 0x0005 0x000A 0x0000 0x00E2 {$zigbeeHexTimeReversed}"])

}


void parse(String description) {

	updatePresence()

	logging("${device} : parse() : $description", "trace")

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		try {

			processMap(descriptionMap)

		} catch (Exception e) {

			// Slice-and-dice the string we receive.
			descriptionMap = description.split(', ').collectEntries {
				entry -> def pair = entry.split(': ')
				[(pair.first()): pair.last()]
			}

			try {

				processMap(descriptionMap)

			} catch (Exception ee) {

				reportToDev(descriptionMap)

			}

		}

	} else {
		
		reportToDev(descriptionMap)

	}

}


void processMap(Map map) {

	if (map.endpoint == "06") {

		logging("${device} : WATER! : We have a water reading!", "error")

	}

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

				ArrayList<String> cmds = []
				cmds += zigbee.readAttribute(0x0201, 0x001C)		// Mode
				cmds += zigbee.readAttribute(0x0201, 0x0024)		// TemperatureSetpointHoldDuration
				sendZigbeeCommands(cmds)

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
					sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0029, [destEndpoint:Integer.valueOf(map.endpoint)]))
					break
				case "04":
					// This always represents normal heating, but we must request the hold state to know if we're scheduled or manual.
					logging("${device} : mode : ${channel} on - requesting hold state", "debug")
					sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0023, [destEndpoint:Integer.valueOf(map.endpoint)]))
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
