/*
 * 
 *  Salus Smart Plug SP600 Driver
 *	
 */


@Field String driverVersion = "v1.16 (14th April 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field String deviceName = "Salus Smart Plug SP600"
@Field boolean debugMode = false
@Field int reportIntervalMinutes = 2
@Field int checkEveryMinutes = 1


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/salus/drivers/salus_smart_plug_sp600.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "Initialize"
		capability "Outlet"
		capability "PowerMeter"
		capability "PresenceSensor"
		capability "Refresh"
		capability "Switch"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0004, 0005, 0006, 0402, 0702, FC01", outClusters: "0019", manufacturer: "Computime", model: "SP600", deviceJoinName: "Salus Smart Plug SP600"

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
	// Called by main configure() method in BirdsLikeWires.library

	device.name = "$deviceName"

	int minReportTime = 10
	int maxReportTime = 20
	int reportableChange = 1

	sendZigbeeCommands(zigbee.configureReporting(0x0702, 0x0400, DataType.INT24, minReportTime, maxReportTime, reportableChange))
	sendZigbeeCommands(zigbee.onOffConfig())

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void refresh() {
	
	logging("${device} : Refreshing", "debug")
	sendZigbeeCommands(zigbee.readAttribute(0x0702, 0x0400))	// power
	sendZigbeeCommands(zigbee.onOffRefresh())					// state

}


void off() {

	sendZigbeeCommands(zigbee.off())

}


void on() {

	sendZigbeeCommands(zigbee.on())

}


void parse(String description) {

	updatePresence()
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

		// Relay configuration and response handling.

		if (map.command == "01" || map.command == "0A") {

			// Relay States

			// 01 - Prompted Refresh
			// 0A - Automated Refresh

			if (map.value == "01") {

				sendEvent(name: "switch", value: "on")
				logging("${device} : Switch : On", "info")

			} else {

				sendEvent(name: "switch", value: "off")
				logging("${device} : Switch : Off", "info")

			}

		} else if (map.command == "07") {

			// Relay Configuration

			logging("${device} : Relay Configuration : Successful", "info")


		} else if (map.command == "0B") {

			// Relay State Confirmations?

			String[] receivedData = map.data
			def String powerStateHex = receivedData[0]

			if (powerStateHex == "01") {

				sendEvent(name: "switch", value: "on")

			} else {

				sendEvent(name: "switch", value: "off")

			}

		} else if (map.command == "00") {

			logging("${device} : skipping state counter message : ${map}", "trace")

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "0702" || map.clusterId == "0702") {

		// Power configuration and response handling.

		// We also use this to update our presence detection given its frequency.

		if (map.command == "07") {

			// Power Configuration

			logging("${device} : Power Reporting Configuration : Successful", "info")

		} else if (map.command == "01" || map.command == "0A") {

			// Power Usage

			// 01 - Prompted Refresh
			// 0A - Automated Refresh

			int powerValue = zigbee.convertHexToInt(map.value)
			powerValue = powerValue <= 1 ? 0 : powerValue		// when off the value can bounce a little
			BigDecimal powerValueCorrected = powerValue / 10
			powerValueCorrected = powerValueCorrected.setScale(1, BigDecimal.ROUND_HALF_UP)
			sendEvent(name: "power", value: powerValueCorrected, unit: "W", isStateChange: false)

			if (map.command == "01") {
				// If this has been requested by the user, return the value in the log.
				logging("${device} : Power : ${powerValue} W", "info")
			}

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}
	
}
