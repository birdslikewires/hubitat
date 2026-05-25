/*
 * 
 *  AlertMe Alarm Sensor Driver
 *	
 */


@Field String driverVersion = "v1.42 (25th May 2026)"
@Field boolean debugMode = false

#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 6
@Field int rangeEveryHours = 6
@Field String deviceName = "AlertMe Alarm Sensor"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/alertme/drivers/alertme_alarm.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "MotionSensor"
		capability "Refresh"
		capability "Sensor"
		capability "SoundSensor"
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

		fingerprint profileId: "C216", inClusters: "00F0,00F1,00F2", outClusters: "", manufacturer: "AlertMe.com", model: "Alarm Detector", deviceJoinName: "$deviceName"

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
	// Called by library configure() method in BirdsLikeWires.alertme

	device.name = "$deviceName"

	state.operatingMode = "normal"

	scheduleRangingIfEnabled()

}


void updateSpecifics() {
	// Called by library updated() method in BirdsLikeWires.library

	scheduleRangingIfEnabled()

}


void processStatus(ZoneStatus status) {

	if (status.isAlarm1Set() || status.isAlarm2Set()) {

		if (device.currentValue("motion") != "active") sendEvent(name: "motion", value: "active")
		if (device.currentValue("sound") != "detected") {
			sendEvent(name: "sound", value: "detected")
			logging("${device} : Sound : Detected", "info")
		}

	} else {

		if (device.currentValue("motion") != "inactive") sendEvent(name: "motion", value: "inactive")
		if (device.currentValue("sound") != "not detected") {
			sendEvent(name: "sound", value: "not detected")
			logging("${device} : Sound : Not Detected", "info")
		}

	}

}


void processMap(Map map) {

	logging("${device} : processMap() : Unhandled cluster ${map.clusterId}.", "trace")

}
