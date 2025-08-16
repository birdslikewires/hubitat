/*
 * 
 *  Bosch Twinguard Driver via Zigbee2MQTT
 *	
 */


@Field String driverVersion = "v0.01 (16th August 2025)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 5
@Field int checkEveryMinutes = 10
@Field String deviceName = "Bosch Twinguard"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/bosch/drivers/bosch_twinguard.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "IlluminanceMeasurement"
		capability "PowerSource"
		capability "RelativeHumidityMeasurement"
		capability "SignalStrength"
		capability "TemperatureMeasurement"

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

	sendEvent(name: "powerSource", value:"battery")

}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	checkDriver()

	// Tasks
	if (json.humidity) sendEvent(name: "humidity", value:"${json.humidity}", unit: "%rh")
	if (json.illuminance) sendEvent(name: "illuminance", value:"${json.illuminance}", unit: "lx")
	if (json.temperature) sendEvent(name: "temperature", value:"${json.temperature}", unit: "°C")

	// Administrative
	if (json.battery) sendEvent(name: "battery", value:"${json.battery}", unit: "%")
	if (json.linkquality) sendEvent(name: "lqi", value: "${json.linkquality}")

	device.name = "${json.device.model}"
	device.label = "${json.device.friendlyName}"
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : parseMQTT : ${json}", "debug")

	updateHealthStatus()

}
