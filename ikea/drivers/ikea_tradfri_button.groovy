/*
 * 
 *  IKEA Tradfri Button Driver
 *	
 */


@Field String driverVersion = "v1.25 (19th October 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 50
@Field String deviceName = "IKEA Tradfri Button"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/ikea/drivers/ikea_tradfri_button.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "Momentary"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "SwitchLevel"

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

	updateDataValue("encoding", "MQTT")

}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	checkDriver()

	// Tasks

	if (json.action) {

		withDebounce("${json.device.networkAddress}", 100, {

			switch("${json.action}") {

				case "on":
					logging("${device} : Action : Button 1 Pressed", "info")
					sendEvent(name: "pushed", value: 1, isStateChange: true)
					break

				case "off":
					logging("${device} : Action : Button 1 Double Pressed", "info")
					sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
					break

				case "open":
					state.levelChangeStart = now()
					logging("${device} : Action : Button 1 Pressed", "info")
					sendEvent(name: "pushed", value: 1, isStateChange: true)
					break

				case "close":
					logging("${device} : Action : Button 2 Pressed", "info")
					sendEvent(name: "pushed", value: 2, isStateChange: true)
					break

				case "stop":
					logging("${device} : Action : Button 1 Released", "info")
					sendEvent(name: "released", value: 1, isStateChange: true)
					levelChange(140)
					break

				case "brightness_move_up":
					state.levelChangeStart = now()
					logging("${device} : Action : Button 1 Held", "info")
					sendEvent(name: "held", value: 1, isStateChange: true)
					break

				case "brightness_stop":
					logging("${device} : Action : Button 1 Released", "info")
					sendEvent(name: "released", value: 1, isStateChange: true)
					levelChange(180)
					break

				default:
					logging("${device} : Action : Type '${json.action}' is an unknown action.", "warn")
					break

			}

			sendEvent(name: "action", value: "${json.action}", isStateChange: true)

		})

	}

	// Admin

	String deviceNameFull = "$deviceName ${json.device.model}"
	device.name = "$deviceNameFull"
	sendEvent(name: "battery", value: "${json.battery ?: 0}", unit: "%")

	switch("${json.device.model}") {

		case "E1766":
			sendEvent(name: "numberOfButtons", value: 2, isStateChange: false)
			break

		case "E1812":
			sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)
			break

	}

	mqttProcessBasics(json)
	updateHealthStatus()

	logging("${device} : processMQTT : ${json}", "debug")

}
