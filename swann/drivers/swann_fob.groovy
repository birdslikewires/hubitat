/*
 * 
 *  Swann Fob Driver via Zigbee2MQTT
 *	
 */


@Field String driverVersion = "v0.01 (21st August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 120
@Field String deviceName = "Swann Fob"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/swann/swann_fob.groovy") {

		capability "Configuration"
		capability "PowerSource"
		capability "PushableButton"
		capability "SignalStrength"

		attribute "action", "string"
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

	device.name = "$deviceName"

	updateDataValue("encoding", "MQTT")
	updateDataValue("isComponent", "false")
	
}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	checkDriver()

	// Tasks

	if (json.action) {

		withDebounce("${json.device.ieeeAddr}", 200, {

			switch("${json.action}") {

				case "home":
					logging("${device} : Action : Button 1 Pressed", "info")
					sendEvent(name: "pushed", value: 1, isStateChange: true)
					break

				case "away":
					logging("${device} : Action : Button 2 Pressed", "info")
					sendEvent(name: "pushed", value: 2, isStateChange: true)
					break

				case "sleep":
					logging("${device} : Action : Button 3 Pressed", "info")
					sendEvent(name: "pushed", value: 3, isStateChange: true)
					break

				case "panic":
					logging("${device} : Action : Button 4 Pressed", "info")
					sendEvent(name: "pushed", value: 4, isStateChange: true)
					break

				default:
					logging("${device} : Action : '${json.action}' is an unknown action.", "info")
					break

			}

			sendEvent(name: "action", value: "${json.action}", isStateChange: true)

		})

	}

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
