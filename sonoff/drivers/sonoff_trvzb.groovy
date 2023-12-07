/*
 * 
 *  Sonoff TRVZB Driver
 *	
 */


@Field String driverVersion = "v0.01 (7th December 2023)"
@Field boolean debugMode = true


#include BirdsLikeWires.library
import groovy.transform.Field

@Field String deviceName = "Sonoff Thermostatic Radiator Valve"
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/sonoff/drivers/sonoff_trvzb.groovy") {

		capability "Configuration"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0006,0020,0201,FC57,FC11", outClusters: "000A,0019", manufacturer: "SONOFF", model: "TRVZB", deviceJoinName: "Sonoff Thermostatic Radiator Valve"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand(int childEndpoint) {

	logging("${device} : Test Command", "info")

}


void configureSpecifics() {
	// Called by general configure() method

	String modelCheck = "${getDeviceDataByName('model')}"
	device.name = "$deviceName $modelCheck"
	//setThermostatDateAndTime(5)

	// Reporting
	//  These had to be constructed manually as configureReporting seems to ignore the [destEndpoint:0x06] additional parameter.
	//  NOTE! Though the water (endpoint 0x06) bind is reported as successful no reports are ever sent. The endpoint needs to be polled. :(
	//        Attributes 1C, 23, 24 and 29 all apply to endpoint 6. Temperature setpoint (12) does not apply (set on the boiler).
	// sendZigbeeCommands([
	// 	"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
	// 	"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0012 0x29 1 43200 {} {}, delay 2000",			// (0x0201, 0x0012) OccupiedHeatingSetpoint
	// 	"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
	// 	"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x001C 0x30 1 43200 {} {}, delay 2000",			// (0x0201, 0x001C) SystemMode 
	// 	"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
	// 	"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0023 0x30 1 43200 {} {}, delay 2000",			// (0x0201, 0x0023) TemperatureSetpointHold 
	// 	"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
	// 	"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0024 0x21 1 43200 {} {}, delay 2000",			// (0x0201, 0x0024) TemperatureSetpointHoldDuration
	// 	"zdo bind 0x${device.deviceNetworkId} 0x05 0x01 0x0201 {${device.zigbeeId}} {}, delay 2000",
	// 	"he cr 0x${device.deviceNetworkId} 0x05 0x0201 0x0029 0x19 1 43200 {} {}, delay 2000"			// (0x0201, 0x0029) ThermostatRunningState
	// ])

	// sendZigbeeCommands(zigbee.configureReporting(0x0201, 0x0000, 0x29, 1, 60))							// (0x0201, 0x0000) Temperature Reporting

}


void updateSpecifics() {
	// Called by library updated() method

	return

}


void auto(int childEndpoint) {
	// Schedule mode. This will run any existing schedule set through the thermostat.

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04, [destEndpoint: childEndpoint])	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x00, [destEndpoint: childEndpoint])	// TemperatureSetpointHold
	sendZigbeeCommands(cmds)

	logging("${device} : System set to schedule mode.", "info")

}


void cool(int childEndpoint) {
	// Being a heat-only system we can only take this to be a request to actively reach a temperature by the only means we have.

	logging("${device} : Request to cool received, but this system does not support active cooling.", "warn")
	heat(childEndpoint)

}


void setCoolingSetpoint(int childEndpoint, BigDecimal temperature) {
	// Being a heat-only system the cooling setpoint can only ever be the same as the heating setpoint.

	logging("${device} : Cooling setpoint requested, but this system does not support active cooling.", "warn")
	setHeatingSetpoint(childEndpoint, temperature)

}


void emergencyHeat(int childEndpoint) {
	// Boost mode.

	int boostTime = 30
	int boostTemp = 2400

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x05, [destEndpoint: childEndpoint], 0)			// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01, [destEndpoint: childEndpoint], 0)			// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, boostTime, [destEndpoint: childEndpoint], 0)	// TemperatureSetpointHoldDuration
	cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, boostTemp, [destEndpoint: childEndpoint], 0) 	// OccupiedHeatingSetpoint
	sendZigbeeCommands(cmds)

	logging("${device} : System boosting to ${boostTemp} for ${boostTime} minutes.", "info")

}


void fanAuto(int childEndpoint) {
	// No controllable fans here.

	logging("${device} : No controllable fans.", "warn")
	return

}


void fanCirculate(int childEndpoint) {
	// No controllable fans here either.

	logging("${device} : No controllable fans.", "warn")
	return

}


void fanOn(int childEndpoint) {
	// Still no controllable fans, stop asking.

	logging("${device} : No controllable fans.", "warn")
	return

}


void heat(int childEndpoint) {
	// Manual mode.

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x04, [destEndpoint: childEndpoint])	// SystemMode
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x01, [destEndpoint: childEndpoint])	// TemperatureSetpointHold
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0x00, [destEndpoint: childEndpoint])	// TemperatureSetpointHoldDuration
	sendZigbeeCommands(cmds)

}


void setHeatingSetpoint(int childEndpoint, BigDecimal temperature) {

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

	sendZigbeeCommands(zigbee.writeAttribute(0x0201, 0x0012, 0x29, temperatureInt, [destEndpoint: childEndpoint]))

}


void setThermostatMode(int childEndpoint, String thermostatMode) {

	logging("${device} : setThermostatMode : ${thermostatMode} ", "debug")

	switch(thermostatMode) {

		case "auto":
			auto(childEndpoint)
			break
		case "off":
			off(childEndpoint)
			break
		case "heat":
			setThermostatModeWithSafety(childEndpoint, thermostatMode)
			break
		case "emergency heat":
			emergencyHeat(childEndpoint)
			break
		case "cool":
			setThermostatModeWithSafety(childEndpoint, thermostatMode)
			break

	}

}


void setThermostatModeWithSafety(int childEndpoint, String thermostatMode) {

	def currentOverrideMinutes = fetchChildStates("overrideMinutes","${childEndpoint}")
	def currentThermostatMode = fetchChildStates("thermostatMode","${childEndpoint}")

	if (currentOverrideMinutes[0] != "0" || currentThermostatMode[0] == "auto") {

		logging("${device} : We're running a schedule. Command 'heat' or 'cool' directly to switch to manual.", "warn")

	} else {

		switch(thermostatMode) {

			case "heat":
				heat(childEndpoint)
				break
			case "cool":
				cool(childEndpoint)
				break

		}

	}

}


void setThermostatFanMode(int childEndpoint, String fanMode) {

	switch(fanMode) {

		case "auto":
			fanAuto(childEndpoint)
			break
		case "circulate":
			fanCirculate(childEndpoint)
			break
		case "on":
			fanOn(childEndpoint)
			break

	}

}


void off(int childEndpoint) {
	// Turns everything off, but respects the frost protect setting.

	ArrayList<String> cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0x0000, [destEndpoint: childEndpoint])	// SystemMode to off
	cmds += zigbee.writeAttribute(0x0201, 0x0023, 0x30, 0x0001, [destEndpoint: childEndpoint])	// TemperatureSetpointHold 
	cmds += zigbee.writeAttribute(0x0201, 0x0024, 0x21, 0xFFFF, [destEndpoint: childEndpoint])	// TemperatureSetpointHoldDuration
	sendZigbeeCommands(cmds)

	//runIn(3,getSystemMode)

}


void getOccupiedHeatingSetpoint(int childEndpoint) {

	sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0012, [destEndpoint: childEndpoint]))

}


void getSystemMode(int childEndpoint) {

	sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x001C, [destEndpoint: childEndpoint]))

}


void getTemperatureSetpointHold(int childEndpoint) {

	sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0023, [destEndpoint: childEndpoint]))

}


void getTemperatureSetpointHoldDuration(int childEndpoint) {

	sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0024, [destEndpoint: childEndpoint]))

}


void getThermostatRunningState(int childEndpoint) {

	sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0029, [destEndpoint: childEndpoint]))

}


void parse(String description) {

	updateHealthStatus()
	checkDriver()

	logging("${device} : parse() : $description", "trace")

	// Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	// if (descriptionMap) {

	// 	def child

	// 	if ("${descriptionMap.endpoint}" == "06") {

	// 		child = fetchChild("BirdsLikeWires","Hive Receiver Water","${descriptionMap.endpoint}")

	// 	} else {

	// 		child = fetchChild("BirdsLikeWires","Hive Receiver Heating","${descriptionMap.endpoint}")

	// 	}

	// 	try {

	// 		child.processMap(descriptionMap)

	// 	} catch (Exception e) {

	// 		// Slice-and-dice the string we receive.
	// 		descriptionMap = description.split(', ').collectEntries {
	// 			entry -> def pair = entry.split(': ')
	// 			[(pair.first()): pair.last()]
	// 		}

	// 		try {

	// 			child.processMap(descriptionMap)

	// 		} catch (Exception ee) {

	// 			reportToDev(descriptionMap)

	// 		}

	// 	}

	// } else {
		
	// 	reportToDev(descriptionMap)

	// }

}
