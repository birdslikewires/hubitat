/*
 * 
 *  Zigbee2MQTT Motion Driver
 *	
 */


@Field String driverVersion = "v1.04 (28th April 2026)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 20
@Field String deviceName = "Zigbee2MQTT Motion"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/zigbee2mqtt/drivers/zigbee2mqtt_motion.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "MotionSensor"
		capability "PowerSource"
		capability "PresenceSensor"
		capability "Sensor"
		capability "SignalStrength"

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

	if (json.containsKey('occupancy')) {

		String previousMotion = "${device.currentValue("motion")}"
		switch("${json.occupancy}") {

			case "true":
				if (previousMotion != "active") {
					sendEvent(name: "motion", value: "active")
					logging("${device} : Motion : Active", "info")
				}
				break

			default:
				if (previousMotion != "inactive") {
					sendEvent(name: "motion", value: "inactive")
					logging("${device} : Motion : Inactive", "info")
				}
				break

		}

	}

	// Admin

	if (json.battery && "${json.battery}" != "${device.currentValue("battery")}") {
		sendEvent(name: "battery", value:"${json.battery}", unit: "%")
	}

	if (json.device?.model && device.name != "${json.device.model}") device.name = "${json.device.model}"

	mqttProcessBasics(json)
	updateHealthStatus()

	logging("${device} : processMQTT : ${json}", "debug")

}
