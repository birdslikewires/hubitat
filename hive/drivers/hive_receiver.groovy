/*
 * 
 *  Hive Receiver Driver
 *	
 */


@Field String driverVersion = "v1.00 (22nd February 2023)"

#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 10
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

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,0009,000A,0201,FD00", outClusters: "000A,0402,0019", manufacturer: "Computime", model: "SLR2", deviceJoinName: "Computime Boiler Controller SLR2", application: "87"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


def testCommand() {

	logging("${device} : Test Command", "info")

	// THERMOSTAT_SYSTEM_CONFIG is an optional attribute. It we add other thermostats we need to determine if they support this and behave accordingly.
	// sendZigbeeCommands( zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_SYSTEM_CONFIG),
	// 		zigbee.readAttribute(FAN_CONTROL_CLUSTER, FAN_MODE_SEQUENCE),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, LOCAL_TEMPERATURE),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, COOLING_SETPOINT),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_RUNNING_STATE),
	// 		zigbee.readAttribute(FAN_CONTROL_CLUSTER, FAN_MODE),
	// 		zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_ALARM_STATE) )

	//sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0400))
	
	//sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0000))

		sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0000))	//Read LocalTemperature
		sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0012))	//Read OccupiedHeatingSetpoint
		sendZigbeeCommands(zigbee.readAttribute(0x201, 0x001C))	//Read SystemMode
		sendZigbeeCommands(zigbee.readAttribute(0x000, 0x0003))	//Read HW Version


	//sendZigbeeCommands(zigbee.configureReporting(0x0201, 0x001C, 0x30, 0, 60, null, [:], 500))

}


void installed() {

	// Runs after first installation.
	logging("${device} : Installed", "info")
	configure()

}


void configure() {

	int randomSixty

	// Tidy up.
	unschedule()
	state.clear()
	state.presenceUpdated = 0
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Schedule presence checking.
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Set device specifics.
	updateDataValue("driver", "$driverVersion")
	configureSpecifics()

	// Notify.
	sendEvent(name: "configuration", value: "complete", isStateChange: false)
	logging("${device} : Configuration complete.", "info")

	updated()
	
}


void configureSpecifics() {
	// Called by general configure() method

	device.name = "Hive Receiver ${modelCheck}"
	setThermostatDateAndTime()

}


void updateSpecifics() {
	// Called by general updated() method

	return

}


void refresh() {

	setThermostatDateAndTime()
	logging("${device} : Refreshed", "info")

}


def heatingOff() {

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x00)	// SystemMode to off
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01)	// TemperatureSetpointHold 
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0xFFFF)	// TemperatureSetpointHoldDuration
	sendZigbeeCommands( cmds )     

}

def heatingManual() {

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04)	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01)	// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0xFFFF)	// TemperatureSetpointHoldDuration
	//cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, 0x076C) // OccupiedHeatingSetpoint (19degC)
	cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, 0x0960) // OccupiedHeatingSetpoint (24degC)
	sendZigbeeCommands( cmds )     

}

def heatingScheduleResume() {

	ArrayList<String> cmds = []

	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04)	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x00)	// TemperatureSetpointHold
	sendZigbeeCommands( cmds )   

}

def heatingBoost() {

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x05)	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01)	// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0x001E)	// TemperatureSetpointHoldDuration (30 mins)
	cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, 0x0898) // OccupiedHeatingSetpoint (22degC)
	sendZigbeeCommands( cmds )  

}

def waterScheduleResume() {

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04, [destEndpoint: 0x06])	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x00, [destEndpoint: 0x06])	// TemperatureSetpointHold
	sendZigbeeCommands( cmds )  

}

def waterOn() {

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04, [destEndpoint: 0x06])	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01, [destEndpoint: 0x06])	// TemperatureSetpointHold
	sendZigbeeCommands( cmds )  

}

def waterOff() {

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x00, [destEndpoint: 0x06])	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x00, [destEndpoint: 0x06])	// TemperatureSetpointHold
	sendZigbeeCommands( cmds )  

}

def waterBoost() {

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x05, [destEndpoint: 0x06])	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01, [destEndpoint: 0x06])	// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0x001E)	// TemperatureSetpointHoldDuration (30 mins)
	sendZigbeeCommands( cmds )  

}


def setThermostatDateAndTime() {

	int zigbeeEpochTime = now()/1000-946684800

	String zigbeeHexTime = zigbee.convertToHexString(zigbeeEpochTime,8)
	String zigbeeHexTimeReversed =
		new StringBuilder(8)
			.append(zigbeeHexTime, 6, 8)
			.append(zigbeeHexTime, 4, 6)
			.append(zigbeeHexTime, 2, 4)
			.append(zigbeeHexTime, 0, 2)
			.toString();

	logging("${device} : zigbeeEpochTime = $zigbeeEpochTime | zigbeeHexTime = $zigbeeHexTime | zigbeeHexTimeReversed = $zigbeeHexTimeReversed", "info")

	sendZigbeeCommands(["he wattr 0x${device.deviceNetworkId} 0x0005 0x000A 0x0000 0x00E2 {$zigbeeHexTimeReversed}"])

}


void parse(String description) {

	updatePresence()

	logging("${device} : parse() : $description", "trace")

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Splurge! : ${description}", "warn")

	}

}


void processMap(Map map) {

	if (map.cluster == "0201") {

		logging("${device} : Got a thermostat message.", "debug")
		reportToDev(map)

		if (map.value == "04") {

			logging("${device} : Heating manual mode?", "debug")

		} else if (map.value == "05") {

			logging("${device} : Heating boost mode?", "debug")

		} else if (map.value == "00") {

			logging("${device} : Heating off mode?", "debug")

		} else {

			logging("${device} : Don't know this one.", "debug")

		}

	} else {

		filterThis(map)

	}
	
}

