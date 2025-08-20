/*
 * 
 *  IKEA Symfonisk Sound Controller Driver
 *	
 */


@Field String driverVersion = "v1.12 (20th August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 50
@Field String deviceName = "IKEA Symfonisk Sound Controller"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/ikea/drivers/ikea_symfonisk_sound_controller.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "Momentary"
		capability "PushableButton"
		capability "Refresh"
		capability "ReleasableButton"
		capability "SwitchLevel"

		attribute "batteryState", "string"
		attribute "direction", "string"
		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "levelChange", "integer"

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

	sendEvent(name: "level", value: 0, isStateChange: false)

}


void updateSpecifics() {

	return

}


void refresh() {

	logging("${device} : Refreshed", "info")

}


void processMQTT(def json) {

	checkDriver()

	// Process the action first!
	if (json.action) debounceAction("${json.action}")

	sendEvent(name: "battery", value:"${json.battery}", unit: "%")
	sendEvent(name: "numberOfButtons", value: 3, isStateChange: false)
	
	String deviceName = "Symfonisk Sound Controller E1744"
	if ("${device.name}" != "$deviceName") device.name = "$deviceName"
	if ("${device.label}" != "${json.device.friendlyName}") device.label = "${json.device.friendlyName}"

	updateDataValue("encoding", "MQTT")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : parseMQTT : ${json}", "debug")

	updateHealthStatus()

}


@Field static Boolean debounceActionParsing = false
void debounceAction(String action) {

	if (debounceActionParsing) {
		logging("${device} : debounceAction : DEBOUNCED", "debug")
		return
	}
	debounceActionParsing = true

	switch(action) {

		case "toggle":
			logging("${device} : Action : Button 1 Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			break

		case "brightness_step_up":
			logging("${device} : Action : Button 1 Double Pressed", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
			break			

		case "brightness_step_down":
			logging("${device} : Action : Button 1 Triple Pressed", "info")
			sendEvent(name: "held", value: 1, isStateChange: true)
			break

		case "brightness_move_up":
			logging("${device} : Action : Button 2 Pressed", "info")
			sendEvent(name: "pushed", value: 2, isStateChange: true)
			sendEvent(name: "held", value: 2, isStateChange: true)
			state.levelChangeStart = now()
			break

		case "brightness_move_down":
			logging("${device} : Action : Button 3 Pressed", "info")
			sendEvent(name: "pushed", value: 3, isStateChange: true)
			sendEvent(name: "held", value: 3, isStateChange: true)
			state.levelChangeStart = now()
			break

		case "brightness_stop":

			int buttonNumber = device.currentState("held").value.toInteger()

			logging("${device} : Action : Button ${buttonNumber} Released", "info")
			sendEvent(name: "released", value: buttonNumber, isStateChange: true)

			if (buttonNumber == 2) {

				levelChange(100,"increase")

			} else {

				levelChange(100,"decrease")

			}

			break

		default:
			logging("${device} : Action : '$action' is an unknown action.", "info")
			break

	}

	pauseExecution 200
	debounceActionParsing = false

}
