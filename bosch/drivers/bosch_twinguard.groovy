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

		capability "AirQuality"
		capability "Alarm"
		capability "Battery"
		capability "CarbonDioxideMeasurement"
		capability "Configuration"
		capability "IlluminanceMeasurement"
		capability "PowerSource"
		capability "RelativeHumidityMeasurement"
		capability "SignalStrength"
		capability "SmokeDetector"
		capability "TemperatureMeasurement"

		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "siren", "string"
		attribute "voc", "number"

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

	device.name = "$deviceName"
	updateDataValue("encoding", "MQTT")
	updateDataValue("isComponent", "false")
	sendEvent(name: "powerSource", value: "battery")

}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	checkDriver()

	// Smoke
	if (json.smoke) {

		sendEvent(name: "smoke", value: "detected")

	} else {

		String noSmoke = (json.self_test) ? "tested" : "clear"
		sendEvent(name: "smoke", value: "$noSmoke")

	}


	if (json.siren_state) {

		String alarmState
		switch("${json.siren_state}") {

			case "burglar":
				alarmState = "strobe"
				break

			case "fire":
				alarmState = "siren"
				break						

			default:
				alarmState = "off"
				break

		}

		sendEvent(name: "alarm", value: "$alarmState")
		sendEvent(name: "siren", value: "${json.siren_state}")

	}

	// Environmental
	if (json.aqi) sendEvent(name: "airQualityIndex", value: "${json.aqi}")
	if (json.co2) sendEvent(name: "carbonDioxide", value: "${json.co2}", unit: "ppm")
	if (json.humidity) sendEvent(name: "humidity", value: "${json.humidity}", unit: "%rh")
	if (json.illuminance) sendEvent(name: "illuminance", value: "${json.illuminance}", unit: "lx")
	if (json.temperature) sendEvent(name: "temperature", value: "${json.temperature}", unit: "°C")
	if (json.voc) sendEvent(name: "voc", value: "${json.voc}", unit: "µg/m³")

	// Device
	if (json.battery) sendEvent(name: "battery", value: "${json.battery}", unit: "%")
	if (json.linkquality) sendEvent(name: "lqi", value: "${json.linkquality}")

	if (json.device) {

		if (json.device.friendlyName) device.label = "${json.device.friendlyName}"
		if (json.device.manufacturerName) updateDataValue("manufacturer", "${json.device.manufacturerName}")
		if (json.device.model) updateDataValue("model", "${json.device.model}")

	}

	logging("${device} : parseMQTT : ${json}", "debug")

	updateHealthStatus()

}
