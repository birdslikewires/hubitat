/*
 * 
 *  AlertMe Motion Sensor Driver
 *	
 */


@Field String driverVersion = "v1.22 (12th October 2022)"


#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 6
@Field int checkEveryMinutes = 1
@Field int rangeEveryHours = 6


metadata {

	definition (name: "AlertMe Motion Sensor", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_motion.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "MotionSensor"
		capability "PresenceSensor"
		capability "Refresh"
		capability "Sensor"
		capability "SignalStrength"
		capability "TamperAlert"
		capability "TemperatureMeasurement"
		capability "VoltageMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "mode", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "C216", inClusters: "00F0,00F1,00F2", outClusters: "", manufacturer: "AlertMe.com", model: "PIR Device", deviceJoinName: "AlertMe Motion Sensor"

	}

}


preferences {
	
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

	device.name = "AlertMe Motion Sensor"

}


void processStatus(ZoneStatus status) {

	if (status.isAlarm1Set() || status.isAlarm2Set()) {

		logging("${device} : Motion : Active", "info")
		sendEvent(name: "motion", value: "active", isStateChange: true)

	} else {

		logging("${device} : Motion : Inactive", "info")
		sendEvent(name: "motion", value: "inactive", isStateChange: true)

	}

}
