/*
 * 
 *  Zigbee2MQTT Climate Driver
 *	
 */


@Field String driverVersion = "v1.04 (28th April 2026)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 20
@Field String deviceName = "Zigbee2MQTT Climate"
@Field BigDecimal humidityInfoLogDelta = 5G
@Field BigDecimal temperatureInfoLogDelta = 0.5G


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/zigbee2mqtt/drivers/zigbee2mqtt_climate.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PowerSource"
		capability "RelativeHumidityMeasurement"
		capability "Sensor"
		capability "SignalStrength"
		capability "TemperatureMeasurement"

		attribute "healthStatus", "enum", ["offline", "online"]

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

	if (json.containsKey('humidity')) {

		Object previousHumidity = device.currentValue("humidity")
		BigDecimal humidity = decimalValueOrNull(json.humidity)
		if (humidity != previousHumidity) {
			sendEvent(name: "humidity", value:"${json.humidity}", unit: "%rh")
		}
		if (hasSignificantDecimalChange(previousHumidity, humidity, humidityInfoLogDelta)) {
			logging("${device} : Humidity : ${json.humidity}%", "info")
		}
	
	}

	if (json.containsKey('local_temperature')) {

		Object previousTemperature = device.currentValue("temperature")
		BigDecimal temperature = decimalValueOrNull(json.local_temperature)
		if (temperature != previousTemperature) {
			sendEvent(name: "temperature", value:"${json.local_temperature}", unit: "°C")
		}
		if (hasSignificantDecimalChange(previousTemperature, temperature, temperatureInfoLogDelta)) {
			logging("${device} : Temperature : ${json.local_temperature}°C", "info")
		}
	
	} else if (json.containsKey('temperature')) {

		Object previousTemperature = device.currentValue("temperature")
		BigDecimal temperature = decimalValueOrNull(json.temperature)
		if (temperature != previousTemperature) {
			sendEvent(name: "temperature", value:"${json.temperature}", unit: "°C")
		}
		if (hasSignificantDecimalChange(previousTemperature, temperature, temperatureInfoLogDelta)) {
			logging("${device} : Temperature : ${json.temperature}°C", "info")
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
