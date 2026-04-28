/*
 * 
 *  Zigbee2MQTT Moisture Driver
 *	
 */


@Field String driverVersion = "v1.03 (28th April 2026)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 20
@Field String deviceName = "Zigbee2MQTT Moisture"
@Field BigDecimal moistureInfoLogDelta = 5G


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

		Object previousMoisture = device.currentValue("moisture")
		BigDecimal moisture = decimalValueOrNull(json.soil_moisture)
		if (moisture != previousMoisture) {
			sendEvent(name: "moisture", value:"${json.soil_moisture}", unit: "%")
		}
		if (hasSignificantDecimalChange(previousMoisture, moisture, moistureInfoLogDelta)) {
			logging("${device} : Moisture : ${json.soil_moisture}%", "info")
		}
	
	}

	// Admin

	if (json.containsKey('battery') && "${json.battery ?: 0}" != "${device.currentValue("battery")}") {
		sendEvent(name: "battery", value:"${json.battery ?: 0}", unit: "%")
	}

	if (json.device?.model && device.name != "${json.device.model}") device.name = "${json.device.model}"

	mqttProcessBasics(json)
	updateHealthStatus()

	logging("${device} : processMQTT : ${json}", "debug")

}
