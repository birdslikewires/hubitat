/*
 * 
 *  Hive Receiver Driver
 *	
 */


@Field String driverVersion = "v0.51 (12th March 2023)"

#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "Hive Receiver", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hive/drivers/hive_receiver.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "PresenceSensor"
		capability "Refresh"
		capability "TemperatureMeasurement"
		capability "ThermostatHeatingSetpoint"
		capability "ThermostatMode"
		capability "ThermostatOperatingState"
		capability "ThermostatSetpoint"

		attribute "heatingBoostRemaining", "number"

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

	//sendZigbeeCommands(zigbee.writeAttribute(0x0201, 0x0024, 0x21, 30, [sourceEndpoint:0x09]))	// TemperatureSetpointHoldDuration

	sendZigbeeCommands([
			"he wattr 0x${device.deviceNetworkId} 0x06 0x0201 0x0024 0x21 {1E}"
	])


	// ArrayList<String> cmds = []

	// cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0x1E, [destEndpoint: 0x06])
	// //cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 65535)		
	// sendZigbeeCommands(cmds)


	//sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0000))	//Read LocalTemperature
	//sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0012))	//Read OccupiedHeatingSetpoint
	//sendZigbeeCommands(zigbee.readAttribute(0x201, 0x001C))	//Read SystemMode
	//sendZigbeeCommands(zigbee.readAttribute(0x000, 0x0003))	//Read HW Version
	//sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0029))	// ThermostatRunningState

	//sendZigbeeCommands(zigbee.writeAttribute(0x0201, 0x0012, 0x29, 29))

	//sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x001C, [destEndpoint:0x06]))	
	//sendZigbeeCommands(zigbee.configureReporting(0x0201, 0x001C, 0x30, 0, 60, null, [:], 500))

}


void configureSpecifics() {
	// Called by general configure() method

	String modelCheck = "${getDeviceDataByName('model')}"
	device.name = "Hive Receiver ${modelCheck}"
	setThermostatDateAndTime()

	// Reporting
	//  These had to be constructed manually as configureReporting seemed to ignore the [destEndpoint:0x06] additional parameter.
	//  NOTE! Though the water (endpoint 0x06) configuration is reported as successful, the behaviour doesn't match heating (endpoint 0x05).
	//        On early firmware some values were reported correctly, but on the latest firmware this doesn't appear to be the case.
	sendZigbeeCommands([
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0012 0x29 1 43200 {} {}, delay 2000",				// OccupiedHeatingSetpoint

		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x001C 0x30 1 43200 {} {}, delay 2000",				// SystemMode (Heating)
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0023 0x30 1 43200 {} {}, delay 2000",				// TemperatureSetpointHold (Heating)
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0024 0x21 1 43200 {} {}, delay 2000",				// TemperatureSetpointHoldDuration (Heating)
		"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0029 0x19 1 43200 {} {}, delay 2000",				// ThermostatRunningState (Heating)

		"zdo bind 0x${device.deviceNetworkId} 0x06 0x01 0x0201 {${device.zigbeeId}} {0x1039}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x06 0x0201 0x001C 0x30 1 43200 {} {}, delay 2000",				// SystemMode (Water)
		"zdo bind 0x${device.deviceNetworkId} 0x06 0x01 0x0201 {${device.zigbeeId}} {0x1039}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x06 0x0201 0x0023 0x30 1 43200 {} {}, delay 2000",				// TemperatureSetpointHold (Water)
		"zdo bind 0x${device.deviceNetworkId} 0x06 0x01 0x0201 {${device.zigbeeId}} {0x1039}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x06 0x0201 0x0024 0x21 1 43200 {} {}, delay 2000",				// TemperatureSetpointHoldDuration (Water)
		"zdo bind 0x${device.deviceNetworkId} 0x06 0x01 0x0201 {${device.zigbeeId}} {0x1039}, delay 2000",
		"he cr 0x${device.deviceNetworkId} 0x06 0x0201 0x0029 0x19 1 43200 {} {}, delay 2000"				// ThermostatRunningState (Water)
	])

	ArrayList<String> cmds = []
	cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 1, 60)		// Temperature Reporting
	sendZigbeeCommands(cmds)

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

	runIn(3,getThermostatMode)

}


void cool() {
	// This is a heat-only system, so cooling and off are essentially the same thing. Open a window!

	off()

}


void emergencyHeat() {
	// Boost mode.

	int boostTime = 1
	int boostTemp = 2300
	logging("${device} : Heating Boost : Attempting to boost to ${boostTemp} for ${boostTime} minutes", "debug")

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x05, [destEndpoint: 0x05], 0)			// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01, [destEndpoint: 0x05], 0)			// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, boostTime, [destEndpoint: 0x05], 0)		// TemperatureSetpointHoldDuration
	cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, boostTemp, [destEndpoint: 0x05], 0) 	// OccupiedHeatingSetpoint
	sendZigbeeCommands(cmds)

	//runIn(3,getThermostatMode)

}


void heat() {
	// Manual mode.

	// With no setpoint specified we have to assume the user wants the current temperature to continue indefinitely.
	String currentSetpoint = device.currentState("heatingSetpoint") ? device.currentState("heatingSetpoint").value : "19.0"
	heat("$currentSetpoint")

}


void heat(String temperature) {
	// Manual mode.

	temperature = temperature.replace(".","")

	int temperatureInt = temperature.toInteger()

	logging("${device} : Heating Setpoint : Setting ${temperature}degC", "info")

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04)				// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01)				// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0x0)				// TemperatureSetpointHoldDuration
	cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, temperatureInt) 	// OccupiedHeatingSetpoint
	sendZigbeeCommands(cmds)

	runIn(3,getThermostatMode)

}


void setHeatingSetpoint(BigDecimal temperature) {

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

}


void off() {
	// Turns everything off, but respects the frost protect setting.

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x00)	// SystemMode to off
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01)	// TemperatureSetpointHold 
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0xFFFF)	// TemperatureSetpointHoldDuration
	sendZigbeeCommands(cmds)

	runIn(3,getThermostatMode)

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

//// Dual channel receiver is a work in progress as despite being IDENTICAL in operation to heat mode, just without the temperature setting,
//// water mode does not appear to properly respond to bind and reporting requests. The receiver replies with success, just doesn't do it.

// def waterScheduleResume() {

// 	ArrayList<String> cmds = []
// 	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04, [destEndpoint: 0x06])	// SystemMode
// 	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x00, [destEndpoint: 0x06])	// TemperatureSetpointHold
// 	sendZigbeeCommands( cmds )  

// }

// def waterOn() {

// 	ArrayList<String> cmds = []
// 	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04, [destEndpoint: 0x06])	// SystemMode
// 	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01, [destEndpoint: 0x06])	// TemperatureSetpointHold
// 	sendZigbeeCommands( cmds )  

// }

// def waterOff() {

// 	ArrayList<String> cmds = []
// 	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x00, [destEndpoint: 0x06])	// SystemMode
// 	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x00, [destEndpoint: 0x06])	// TemperatureSetpointHold
// 	sendZigbeeCommands( cmds )  

// }

// def waterBoost() {

// 	ArrayList<String> cmds = []
// 	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x05, [destEndpoint: 0x06])	// SystemMode
// 	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01, [destEndpoint: 0x06])	// TemperatureSetpointHold
// 	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0x001E)	// TemperatureSetpointHoldDuration (30 mins)
// 	sendZigbeeCommands( cmds )  

// }


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

		logging("${device} : WATER! : We have a water reading!", "debug")

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
					break
				default:
					logging("${device} : mode : unknown ${channel} mode received", "warn")
					break

			}

		} else if (map.attrId == "0023") {
			// TemperatureSetpointHold

			logging("${device} : setpoint hold : ${map.value} on endpoint ${map.endpoint}", "debug")

			String channel = ("${map.endpoint}" == "05") ? "thermostatMode" : "waterstatMode"

			switch(map.value) {

				case "00":
					// This always represents scheduled mode as the hold is off, so the schedule is running.
					sendEvent(name: "${channel}", value: "auto")
					logging("${device} : setpoint hold : ${map.value} on endpoint ${map.endpoint}", "debug")
					break
				case "01":
					// The hold is on, so the schedule is paused. We're in some sort of manual mode.
					//   NOTE! If the system was in schedule (auto) mode and the setpoint altered without changing from that mode,
					//         the system switches to a hybrid mode where the altered setpoint runs for the duration until the
					//         next programme change in the schedule. At that point the programme resumes, but the thermostat
					//         always reports "SCH" despite being in a manual state.
					sendEvent(name: "${channel}", value: "heat")
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
