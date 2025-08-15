/*
 * 
 *  Lidl Smart Extension Driver
 *  
 *  Supports: HG06338
 *	
 */


@Field String driverVersion = "v1.13 (7th December 2023)"
@Field boolean debugMode = false


#include BirdsLikeWires.library
import groovy.transform.Field

@Field String deviceMan = "Lidl"
@Field String deviceType = "Smart Extension"

@Field int reportIntervalMinutes = 10
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "$deviceMan $deviceType", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/lidl/drivers/lidl_smart_extension.groovy") {

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

		fingerprint profileId: "1251", inClusters: "0000, 0003, 0004, 0005, 0006", outClusters: "0021", manufacturer: "_TZ3000_vmpbygs5", model: "TS011F", deviceJoinName: "$deviceMan $deviceType HG06338"

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
	device.name = "$deviceMan $deviceType HG06338"

	// Store relay count and create children.
	state.relayCount = 3
	for (int i = 1; i <= state.relayCount; i++) {
		fetchChild("hubitat", "Generic Component Switch", "0$i")
	}

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

}


void on() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x01 {}"])

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

		if (map.command == "01") {

			// Relay States (Refresh)

			if (map.value == "01") {

				def cd = fetchChild("hubitat", "Generic Component Switch", "${map.endpoint}")
				cd.parse([[name:"switch", value:"on"]])

				sendEvent(name: "switch", value: "on")
				logging("${device} : Switch ${map.endpoint} : On", "info")

			} else {

				def cd = fetchChild("hubitat", "Generic Component Switch", "${map.endpoint}")
				cd.parse([[name:"switch", value:"off"]])

				def currentChildStates = fetchChildStates("switch","${cd.id}")
				logging("${device} : currentChildStates : ${currentChildStates}", "debug")

				if (currentChildStates.every{it == "off"}) {
					logging("${device} : All Devices Off", "info")
					sendEvent(name: "switch", value: "off")
				}

				logging("${device} : Switch ${map.endpoint} : Off", "info")

			}

		} else if (map.command == "07") {

			// Relay Configuration

			logging("${device} : Relay Configuration : Successful", "info")

		} else if (map.command == "0A") {

			// Relay States (Local Actuation)

			if (map.value == "01") {

				def cd = fetchChild("hubitat", "Generic Component Switch", "${map.endpoint}")
				cd.parse([[name:"switch", value:"on"]])
				refresh()
				logging("${device} : Local Switch ${map.endpoint} : On", "info")

			} else {

				def cd = fetchChild("hubitat", "Generic Component Switch", "${map.endpoint}")
				cd.parse([[name:"switch", value:"off"]])
				refresh()
				logging("${device} : Local Switch ${map.endpoint} : Off", "info")

			}			

		} else if (map.command == "0B") {

			// Relay States (Remote Actuation)

			String[] receivedData = map.data
			String powerStateHex = receivedData[0]

			if (powerStateHex == "01") {

				def cd = fetchChild("hubitat", "Generic Component Switch", "${map.sourceEndpoint}")
				cd.parse([[name:"switch", value:"on"]])
				sendEvent(name: "switch", value: "on")
				logging("${device} : Switched ${map.sourceEndpoint} : On", "info")

			} else {

				def cd = fetchChild("hubitat", "Generic Component Switch", "${map.sourceEndpoint}")
				cd.parse([[name:"switch", value:"off"]])

				def currentChildStates = fetchChildStates("switch","${cd.id}")
				logging("${device} : currentChildStates : ${currentChildStates}", "debug")

				if (currentChildStates.every{it == "off"}) {
					logging("${device} : All Devices Off", "info")
					sendEvent(name: "switch", value: "off")
				}

				logging("${device} : Switched ${map.sourceEndpoint} : Off", "info")

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
