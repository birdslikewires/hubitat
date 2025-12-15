/*
 * 
 *  Bosch Twinguard Driver via Zigbee2MQTT
 *	
 */


@Field String driverVersion = "v1.02 (14th December 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 5
@Field String deviceName = "Bosch Room Thermostat II"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/bosch/drivers/bosch_room_thermostat_ii.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "HealthCheck"
		capability "PowerSource"
		capability "Refresh"
		capability "RelativeHumidityMeasurement"
		capability "Sensor"
		capability "SignalStrength"
		capability "TemperatureMeasurement"

		attribute "displayBrightness", "number"
		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "temperatureLocal", "number"
		attribute "temperatureRemote", "number"

		command "displayOn"

		if (debugMode) {
			command "testCommand"
		}

	}

}


preferences {
	
	input name: "remoteAsPrimary", type: "bool", title: "Use remote sensor for primary temperature reading.", defaultValue: false

	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")

}


void configureSpecifics() {

	device.name = "$deviceName"
	updateDataValue("encoding", "MQTT")
	updateDataValue("isComponent", "false")

}


void updateSpecifics() {

	configureSpecifics()

}


void refresh() {

	String ieee = getDataValue("ieee")
	parent.publishMQTT("$ieee", "get", "{\"operating_mode\": \"\"}")		// Any get request seems to trigger a full report.

}


void ping() {

	refresh()

}


void off() {

	String ieee = getDataValue("ieee")
	parent.publishMQTT("$ieee", "set", "{\"alarm\": \"stop\"}")
	parent.publishMQTT("$ieee", "get", "{\"alarm\": \"\"}")		// Necessary otherwise it doesn't update until the next regular report.

}


void displayOn() {

	int currentBrightness = device.currentState("displayBrightness").value.toInteger()
	String ieee = getDataValue("ieee")
	parent.publishMQTT("$ieee", "set", "{\"display_brightness\": $currentBrightness}")

}


void processMQTT(def json) {

	checkDriver()

	// Environmental
	if (json.humidity) sendEvent(name: "humidity", value: "${json.humidity}", unit: "%rh")
	if (json.cable_sensor_temperature) sendEvent(name: "temperatureRemote", value: "${json.cable_sensor_temperature}", unit: "°C")
	if (json.local_temperature) sendEvent(name: "temperatureLocal", value: "${json.local_temperature}", unit: "°C")

	BigDecimal primaryTemperature = remoteAsPrimary ? json.cable_sensor_temperature : json.local_temperature
	sendEvent(name: "temperature", value: "${primaryTemperature}", unit: "°C")

	// Device
	if (json.display_brightness) sendEvent(name: "displayBrightness", value: "${json.display_brightness}")

	// Admin
	mqttProcessBasics(json)
	updateHealthStatus()
	logging("${device} : processMQTT : ${json}", "debug")

}
