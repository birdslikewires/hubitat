/*
 * 
 *  Example Device Driver via Zigbee2MQTT
 *	
 */


@Field String driverVersion = "v0.01 (16th August 2025)"
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

	updateDataValue("encoding", "MQTT")
	updateDataValue("isComponent", "false")

}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	checkDriver()

	// Tasks


	// Admin

	sendEvent(name: "lqi", value: "${json.linkquality}".toInteger())
	String powerSource = "${json.device.powerSource}".toLowerCase().contains("mains") ? "mains" : "battery"
	sendEvent(name: "powerSource", value:"$powerSource")

	device.name = "${json.device.model}"
	device.label = "${json.device.friendlyName}"

	updateDataValue("ieee", "${json.device.ieeeAddr}")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : parseMQTT : ${json}", "debug")

	updateHealthStatus()

}
