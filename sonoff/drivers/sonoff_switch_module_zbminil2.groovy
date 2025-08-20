/*
 * 
 *  Sonoff Switch Module ZBMINIL2 Driver
 *	
 */


@Field String driverVersion = "v1.02 (18th March 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 10
@Field String deviceName = "Sonoff Switch Module ZBMINIL2"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/sonoff/drivers/sonoff_switch_module_zbminil2.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "HealthCheck"
		capability "Refresh"
		capability "Switch"

		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "mode", "string"

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0006,0007,0B05,FC57", outClusters: "0019", manufacturer: "SONOFF", model: "ZBMINIL2", deviceJoinName: "$deviceName"

	}

}


preferences {
	
	input name: "flashEnabled", type: "bool", title: "Enable flash", defaultValue: false
	input name: "flashRate", type: "number", title: "Flash rate (ms)", range: "500..5000", defaultValue: 1000

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

	// Unbind from polling control cluster and set reporting intervals.
	int reportIntervalMinSeconds = 3
	int reportIntervalMaxSeconds = reportIntervalMinutes * 60

	ArrayList<String> cmds = [
		"zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",'delay 200',
	]
	cmds += zigbee.configureReporting(0x0006, 0x0000, 0x0010, reportIntervalMinSeconds, reportIntervalMaxSeconds, null)
	sendZigbeeCommands(cmds)

	// Set device name.
	String deviceModel = getDeviceDataByName('model')
	device.name = "$deviceName"

	// Always set to 'static' to ensure we're never stuck in 'flashing' mode.
	sendEvent(name: "mode", value: "static", isStateChange: false)

	// Cleanup
	removeDataValue("application")

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void ping() {

	logging("${device} : Ping", "info")
	refresh()

}


void refresh() {

	sendZigbeeCommands(["he rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"])
	logging("${device} : Refreshed", "info")

}


void off() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"])
	sendEvent(name: "mode", value: "static")

}


void on() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}"])
	sendEvent(name: "mode", value: "static")

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


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	if (map.cluster == "0006" || map.clusterId == "0006") {
		// Relay configuration and response handling.
		// State confirmation and command receipts.

		if (map.command == "07") {
			// Relay Configuration

			logging("${device} : Relay Configuration : Successful", "debug")

		} else if (map.command == "01" || map.command == "0A" || map.command == "0B") {
			// Relay States
			// It seems that command "0A" denotes a state change, command "0B" is not necessarily.

			String relayActuated = (map.command == "01" || map.command == "0A") ? map.endpoint : map.destinationEndpoint
			String relayState = (map.command == "01" || map.command == "0A") ? map.value : map.data[0]

			if (map.command == "0A") {

				if (relayState == "00") {

					sendEvent(name: "switch", value: "off", isStateChange: true)
					logging("${device} : Switched $relayActuated : Off", "info")

				} else {

					sendEvent(name: "switch", value: "on", isStateChange: true)
					logging("${device} : Switched $relayActuated : On", "info")

				}

			} else {

				if (relayState == "00") {

					sendEvent(name: "switch", value: "off", isStateChange: false)
					logging("${device} : Requested $relayActuated : Off", "info")

				} else {

					sendEvent(name: "switch", value: "on", isStateChange: false)
					logging("${device} : Requested $relayActuated : On", "info")

				}

			}

		} else if (map.command == "00") {

			logging("${device} : Skipped : State Counter Message", "debug")

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}
	
}
