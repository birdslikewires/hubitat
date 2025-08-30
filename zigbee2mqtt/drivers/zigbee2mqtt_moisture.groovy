/*
 * 
 *  Zigbee2MQTT Moisture Driver
 *	
 */


@Field String driverVersion = "v1.00 (30th August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 20
@Field String deviceName = "Zigbee2MQTT Moisture"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/zigbee2mqtt/drivers/zigbee2mqtt_moisture.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PowerSource"
		capability "RelativeHumidityMeasurement"
		capability "Sensor"
		capability "SignalStrength"
		capability "TemperatureMeasurement"

		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "moisture", "integer"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void configureSpecifics() {

	updateDataValue("encoding", "MQTT")

}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	checkDriver()

	// Tasks

	if (json.containsKey('soil_moisture')) {

		logging("${device} : Moisture : ${json.soil_moisture}%", "info")
		sendEvent(name: "moisture", value:"${json.soil_moisture}", unit: "%")
	
	}

	// Admin

	if (json.containsKey('battery')) sendEvent(name: "battery", value:"${json.battery ?: 0}", unit: "%")

	device.name = "${json.device.model}"

	mqttProcessBasics(json)
	updateHealthStatus()

	logging("${device} : processMQTT : ${json}", "debug")

}
