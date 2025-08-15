/*
 * 
 *  Aurora Dimmer AU-A1ZB2WDM Driver
 *	
 */


@Field String driverVersion = "v1.08 (26th August 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 10
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "Aurora Dimmer AU-A1ZB2WDM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/aurora/drivers/aurora_dimmer_a1zb2wdm.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "Light"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"

		command "indicatorOn"
		command "indicatorOff"

		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "indicator", "enum", ["off", "on"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "8E63", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0019", manufacturer: "Aurora", model: "WallDimmerMaster", deviceJoinName: "Aurora Dimmer AU-A1ZB2WDM"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: true
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: true
	
}


void testCommand() {

	logging("${device} : Test Command", "info")

}


void configureSpecifics() {

	device.name = "Aurora Dimmer AU-A1ZB2WDM"

	int reportDelay = reportIntervalMinutes * 60

	ArrayList<String> cmds = []
	cmds += zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, reportDelay)		// report 
	sendZigbeeCommands(cmds)

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void refresh() {
	
	logging("${device} : Refreshing", "debug")
	sendZigbeeCommands([
		"he rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x0000 {}",	// state
		"he rattr 0x${device.deviceNetworkId} 0x01 0x0008 0x0000 {}",	// level
		"he rattr 0x${device.deviceNetworkId} 0x03 0x0006 0x0000 {}"	// indicator state
	])

}


void off() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"])
	//sendZigbeeCommands(zigbee.off())  // same thing

}


void on() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}"])
	//sendZigbeeCommands(zigbee.on())  // same thing

}


void indicatorOff() {

	// Turns blue backlight LED off. Does not persist with power cycling.
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x03 0x0006 0x00 {}"])

}


void indicatorOn() {

	// Turns blue backlight LED on.
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x03 0x0006 0x01 {}"])

}


void setLevel(BigDecimal level) {

	setLevel(level,1)

}


void setLevel(BigDecimal level, BigDecimal duration) {

	BigDecimal safeLevel = level <= 98 ? level : 100
	safeLevel = safeLevel < 0 ? 0 : safeLevel
	String hexLevel = percentageToHex(safeLevel.intValue())

	BigDecimal safeDuration = duration <= 25 ? (duration*10) : 255
	String hexDuration = Integer.toHexString(safeDuration.intValue())

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Got level request of '${level}' (${safeLevel}%) [${hexLevel}] over '${duration}' (${safeDuration} decisecond${pluralisor}) [${hexDuration}].", "debug")

	// The command data is made up of three hex values, the first byte is the level, second is duration, third always seems to be '00'.
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0008 0x04 {${hexLevel} ${hexDuration} 00}"])

}


void checkLevel() {

	unschedule(checkLevel)
	logging("${device} : Checking Level", "debug")
	sendZigbeeCommands([
		"he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0x0000 {}"
	])

}


void parse(String description) {

	updateHealthStatus()
	checkDriver()

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		logging("${device} : Parse : ${descriptionMap}", "debug")
		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Parse : ${description}", "error")

	}

}


void processMap(map) {

	if (map.cluster == "0006" || map.clusterId == "0006") {

		// On or off.

		String dimmerOrIndicator = (map.endpoint == "03" || map.sourceEndpoint == "03") ? "Indicator" : "Dimmer"

		String deviceData = (map.data != null) ? map.data[0] : ""

		if (map.value == "01" || deviceData == "01") {

			// On

			if (dimmerOrIndicator == "Dimmer") {

				sendEvent(name: "switch", value: "on")
				runIn(12, checkLevel)

			} else {

				sendEvent(name: "indicator", value: "on")

			}

			logging("${device} : ${dimmerOrIndicator} On", "info")

		} else if (map.value == "00" || deviceData == "00") {

			// Off

			if (dimmerOrIndicator == "Dimmer") {

				sendEvent(name: "switch", value: "off")
				runIn(12, checkLevel)

			} else {

				sendEvent(name: "indicator", value: "off")

			}

			logging("${device} : ${dimmerOrIndicator} Off", "info")

		} else {

			filterThis(map)

		}

	} else if (map.cluster == "0008" || map.clusterId == "0008") {

		// Level

		if (map.command == "01" || map.command == "0A") {

			// Reading

			int currentLevel = hexToPercentage("${map.value}")
			sendEvent(name: "level", value: "${currentLevel}")
			logging("${device} : Level : ${currentLevel}", "info")

		} else if (map.command == "0B") {

			// Status

			logging("${device} : fading", "debug")

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}
	
}
