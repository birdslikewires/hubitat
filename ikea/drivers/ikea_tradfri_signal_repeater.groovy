/*
 * 
 *  IKEA Tradfri Signal Repeater Driver
 *	
 */


@Field String driverVersion = "v1.08 (15th August 2025)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 10
@Field int checkEveryMinutes = 2


metadata {

	definition (name: "IKEA Tradfri Signal Repeater", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/ikea/drivers/ikea_tradfri_signal_repeater.groovy") {

		capability "Configuration"
		capability "HealthCheck"
		capability "Initialize"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,0009,0B05,1000,FC7C", outClusters: "0019,0020,1000", manufacturer: "IKEA of Sweden", model: "TRADFRI signal repeater", deviceJoinName: "Tradfri Signal Repeater", application: "20"

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

	// Reporting
	int reportIntervalSeconds = reportIntervalMinutes * 60
	sendZigbeeCommands(zigbee.configureReporting(0x0000, 0x0005, 0x0042, 0, reportIntervalSeconds, 1, [:], 200))	// Send model information.

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void ping() {

	logging("${device} : Ping", "info")
	sendZigbeeCommands(["he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x0005 {}"])	// Request model information.

}


void parse(String description) {

	updateHealthStatus()
	checkDriver()

	logging("${device} : parse() : $description", "trace")

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		// We don't even bother with a processMap() for this device.
		filterThis(descriptionMap)

	} else {
		
		reportToDev(descriptionMap)

	}

}
