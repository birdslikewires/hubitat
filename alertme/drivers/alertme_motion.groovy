/*
 * 
 *  AlertMe Motion Sensor Driver
 *	
 */


@Field String driverVersion = "v1.37 (5th August 2026)"
@Field boolean debugMode = false

#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 6
@Field int rangeEveryHours = 6
@Field String deviceName = "AlertMe Motion Sensor"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/alertme/drivers/alertme_motion.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "MotionSensor"
		//capability "PresenceSensor"	// to be re-enabled as this is a real thing this device would be used for
		capability "Refresh"
		capability "Sensor"
		capability "TamperAlert"
		capability "TemperatureMeasurement"
		capability "VoltageMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "lqi", "number"

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "C216", inClusters: "00F0,00F1,00F2", outClusters: "", manufacturer: "AlertMe.com", model: "PIR Device", deviceJoinName: "$deviceName"

	}

}


preferences {
	
	input name: "periodicRanging", type: "bool", title: "Enable diagnostics", defaultValue: false
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F6 {11 00 FC 01} {0xC216}"])	   // version information request
	
}


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.alertme

	device.name = "$deviceName"

	normalMode()

	scheduleRangingIfEnabled()

}


void updateSpecifics() {
	// Called by library updated() method in BirdsLikeWires.library

	scheduleRangingIfEnabled()

}


void processStatus(ZoneStatus status) {

	if (status.isAlarm1Set() || status.isAlarm2Set()) {

		if (device.currentValue("motion") != "active") {
			sendEvent(name: "motion", value: "active")
			logging("${device} : Motion : Active", "info")
		}

	} else {

		if (device.currentValue("motion") != "inactive") {
			sendEvent(name: "motion", value: "inactive")
			logging("${device} : Motion : Inactive", "info")
		}

	}

}


void processMap(Map map) {

	logging("${device} : processMap() : Unhandled cluster ${map.clusterId}.", "trace")

}
