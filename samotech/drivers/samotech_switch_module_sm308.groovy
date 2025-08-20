/*
 * 
 *  Samotech SM308 Switch Module Driver
 *	
 */


@Field String driverVersion = "v1.17 (20th August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 1
@Field String deviceMan = "Samotech"
@Field String deviceType = "Switch Module"


metadata {

	definition (name: "$deviceMan $deviceType SM308", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/samotech/drivers/samotech_switch_module_sm308.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "HealthCheck"
		capability "Refresh"
		capability "Switch"

		command "flash"

		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "mode", "string"

		if (debugMode) {
			command "testCommand"
		}

		// The SM308 is the original model and requires a neutral wire. This was replaced by the SM308-S, which can operate with or without neutral.
		// The SM308-2CH has two relays, but requires neutral.
		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,1000", outClusters: "0019", manufacturer: "Samotech", model: "SM308", deviceJoinName: "$deviceMan $deviceType SM308"
		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,1000", outClusters: "0019", manufacturer: "Samotech", model: "SM308-S", deviceJoinName: "$deviceMan $deviceType SM308-S"
		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0702,0B04,0B05,1000", outClusters: "0019", manufacturer: "Samotech", model: "SM308-2CH", deviceJoinName: "$deviceMan $deviceType SM308-2CH"

	}

}


preferences {
	
	input name: "flashEnabled", type: "bool", title: "Enable flash", defaultValue: false
	input name: "flashRate", type: "number", title: "Flash rate (ms)", range: "500..5000", defaultValue: 1000

	if ("${getDeviceDataByName('model')}" == "SM308-2CH") {
		input name: "flashRelays", type: "enum", title: "Flash relay", options:[["FF":"Both"],["01":"Relay 1"],["02":"Relay 2"]]
	}

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
	String deviceModel = getDeviceDataByName('model')
	device.name = "$deviceMan $deviceType $deviceModel"

	// Store relay count and create children.
	state.relayCount = ("${getDeviceDataByName('model')}" == "SM308-2CH") ? 2 : 1

	if (state.relayCount > 1) {
		for (int i = 1; i <= state.relayCount; i++) {
			fetchChild("hubitat", "Generic Component Switch", "0$i")
		}
	} else {
		deleteChildren()
	}

	// Always set to 'static' to ensure we're never stuck in 'flashing' mode.
	sendEvent(name: "mode", value: "static", isStateChange: false)

	// Reporting
	int reportIntervalSeconds = reportIntervalMinutes * 60
	sendZigbeeCommands(zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, reportIntervalSeconds, 0x00))

}


void updateSpecifics() {

	configureSpecifics()

}


void ping() {

	logging("${device} : Ping", "info")
	refresh()

}


void refresh() {

	sendZigbeeCommands(["he rattr 0x${device.deviceNetworkId} 0xFF 0x0006 0x00 {}"])
	logging("${device} : Refreshed", "info")

}


void off() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x00 {}"])
	sendEvent(name: "mode", value: "static")

}


void on() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x01 {}"])
	sendEvent(name: "mode", value: "static")

}


void flash() {

	if (!flashEnabled) {
		logging("${device} : Flash : Disabled", "warn")
		return
	}

	if (!flashRelays && "${getDeviceDataByName('model')}" == "SM308-2CH") {
		logging("${device} : Flash : No relay chosen in preferences.", "warn")
		return
	}

	logging("${device} : Flash : Rate of ${flashRate ?: 1000} ms", "info")
	sendEvent(name: "mode", value: "flashing")
	pauseExecution 200
    flashOn()

}


void flashOn() {

	String mode = device.currentState("mode").value
	logging("${device} : flashOn : Mode is ${mode}", "debug")

    if (mode != "flashing") return
    runInMillis((flashRate ?: 1000).toInteger(), flashOff)

	String flashEndpoint = "FF"
	if (flashRelays) flashEndpoint = flashRelays

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x${flashEndpoint} 0x0006 0x01 {}"])

}


void flashOff() {

	String mode = device.currentState("mode").value
	logging("${device} : flashOn : Mode is ${mode}", "debug")

    if (mode != "flashing") return
	runInMillis((flashRate ?: 1000).toInteger(), flashOn)

	String flashEndpoint = "FF"
	if (flashRelays) flashEndpoint = flashRelays

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x${flashEndpoint} 0x0006 0x00 {}"])

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

		if (map.command == "01") {
			// Relay States (Refresh)

			if (map.value == "00") {

				if (state.relayCount > 1) {

					def childDevice = fetchChild("hubitat", "Generic Component Switch", "${map.endpoint}")
					childDevice.parse([[name:"switch", value:"off"]])

					def currentChildStates = fetchChildStates("switch","${childDevice.id}")
					logging("${device} : currentChildStates : ${childDevice.id} ${currentChildStates}", "debug")

					if (currentChildStates.every{it == "off"}) {
						logging("${device} : Switch : All Off", "info")
						sendEvent(name: "switch", value: "off")
					}

				} else {

					sendEvent(name: "switch", value: "off")

				}

				logging("${device} : Switch ${map.endpoint} : Off", "info")

			} else {

				if (state.relayCount > 1) {
					def childDevice = fetchChild("hubitat", "Generic Component Switch", "${map.endpoint}")
					childDevice.parse([[name:"switch", value:"on"]])
				}

				sendEvent(name: "switch", value: "on")
				logging("${device} : Switch ${map.endpoint} : On", "info")

			}

		} else if (map.command == "07") {

			processConfigurationResponse(map)

		} else if (map.command == "0A" || map.command == "0B") {
			// Relay States

			String relayActuated = (map.command == "0A") ? map.endpoint : map.sourceEndpoint
			String relayState = (map.command == "0A") ? map.value : map.data[0]

			if (relayState == "00") {

				if (state.relayCount > 1) {

					def childDevice = fetchChild("hubitat", "Generic Component Switch", "$relayActuated")
					childDevice.parse([[name:"switch", value:"off"]])

					def currentChildStates = fetchChildStates("switch","${childDevice.id}")
					logging("${device} : currentChildStates : ${currentChildStates}", "debug")

					if (currentChildStates.every{it == "off"}) {

						debounceParentState("switch", "off", "All Devices Off", "info", 100)

					}

				} else {

					sendEvent(name: "switch", value: "off")

				}

				logging("${device} : Switched $relayActuated : Off", "info")

			} else {

				if (state.relayCount > 1) {
					def childDevice = fetchChild("hubitat", "Generic Component Switch", "$relayActuated")
					childDevice.parse([[name:"switch", value:"on"]])
				}

				sendEvent(name: "switch", value: "on")
				logging("${device} : Switched $relayActuated : On", "info")

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
