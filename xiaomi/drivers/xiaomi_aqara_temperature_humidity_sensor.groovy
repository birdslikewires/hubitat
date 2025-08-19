/*
 *
 *  Xiaomi Aqara Temperature and Humidity Sensor Driver
 *
 */


@Field String driverVersion = "v1.17 (19th August 2025)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 60
@Field String deviceName = "Xiaomi Aqara Temperature and Humidity Sensor"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/xiaomi/drivers/xiaomi_aqara_temperature_humidity_sensor.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PressureMeasurement"
		capability "RelativeHumidityMeasurement"
		capability "Sensor"
		capability "TemperatureMeasurement"
		capability "VoltageMeasurement"

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

	removeDataValue("isComponent")
	removeDataValue("label")
	removeDataValue("name")

}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	// I've not got this far yet. :'D

	return

}