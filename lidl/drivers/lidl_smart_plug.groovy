/*
 * 
 *  Lidl Smart Plug Driver
 *
 *  Supports: HG06337
 *	
 */


@Field String driverVersion = "v1.05 (29th August 2023)"
@Field boolean debugMode = false


#include BirdsLikeWires.library
import groovy.transform.Field

@Field String deviceMan = "Lidl"
@Field String deviceType = "Smart Plug"

@Field int reportIntervalMinutes = 10
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "$deviceMan $deviceType", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/lidl/drivers/lidl_smart_plug.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "HealthCheck"
		capability "Outlet"
		capability "Refresh"
		capability "Switch"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "976B", inClusters: "0000, 0003, 0004, 0005, 0006", outClusters: "0021", manufacturer: "_TZ3000_00mk2xzy", model: "TS011F", deviceJoinName: "$deviceMan $deviceType HG06337"

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

	requestBasic()

	// Set device name.
	device.name = "$deviceMan $deviceType HG06337"

	// Reporting
	int minReportTime = 1
	int maxReportTime = reportIntervalMinutes * 60
	int reportableChange = 1
	sendZigbeeCommands(zigbee.configureReporting(0x0006, 0x0000, 0x0010, minReportTime, maxReportTime, reportableChange))
	
}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void off() {

	sendZigbeeCommands(zigbee.off())

}


void on() {

	sendZigbeeCommands(zigbee.on())

}


void ping() {

	sendZigbeeCommands(zigbee.onOffRefresh())
	logging("${device} : Ping", "info")

}


void refresh() {
	
	sendZigbeeCommands(zigbee.onOffRefresh())
	logging("${device} : Refreshed", "info")

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

	logging("${device} : processMap() : ${map}", "trace")

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

	} else {

		filterThis(map)

	}
	
}
