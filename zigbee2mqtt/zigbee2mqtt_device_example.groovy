/*
 * 
 *  Example Device Driver via Zigbee2MQTT
 *	
 */


@Field String driverVersion = "v0.02 (27th August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 5
@Field String deviceName = "Example Device"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/zigbee2mqtt/zigbee2mqtt_device_example.groovy") {

		capability "Configuration"
		capability "PowerSource"
		capability "SignalStrength"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

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

	updateDataValue("encoding", "MQTT")		// Must be set here for the remainder of configure() to work.

}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	checkDriver()

	// Tasks


	// Admin

	device.name = "${json.device.model}"	// Not handled in mqttProcessBasics(json) because custom drivers may want to display this differently.

	mqttProcessBasics(json)
	updateHealthStatus()

	logging("${device} : processMQTT : ${json}", "debug")

}
