/*
 * 
 *  Lidl Remote Driver
 *	
 */


@Field String driverVersion = "v1.02 (29th August 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field String deviceName = "Lidl Remote"
@Field boolean debugMode = true
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/lidl/drivers/lidl_remote.groovy") {

		capability "Configuration"
		capability "HoldableButton"
		capability "Initialize"
		capability "Momentary"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "SwitchLevel"

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

	return
	
}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	// Process the action first!
	if (json.action) debounceAction("${json.action}")

	switch("${json.device.model}") {

		case "FB20-002":
			sendEvent(name: "numberOfButtons", value: 4, isStateChange: false)
			break

		default:
			logging("${device} : Unknown device type '${json.device.model}'.", "warn")
			break

	}

	String thisDeviceName = "$deviceName ${json.device.model}"
	if ("${device.name}" != "$thisDeviceName") device.name = "$thisDeviceName"
	if ("${device.label}" != "${json.device.friendlyName}") device.label = "${json.device.friendlyName}"

	updateDataValue("encoding", "MQTT")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : parseMQTT : ${json}", "debug")

	updateHealthStatus()
	checkDriver()

}


@Field static Boolean debounceActionParsing = false
void debounceAction(String action) {

	if (debounceActionParsing) {
		logging("${device} : parseMQTT : DEBOUNCED", "debug")
		return
	}
	debounceActionParsing = true

	switch(action) {

		case "off":
			logging("${device} : Action : Button 1 Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			break

		case "on":
			logging("${device} : Action : Button 2 Pressed", "info")
			sendEvent(name: "pushed", value: 2, isStateChange: true)
			break

		case "brightness_step_up":
			logging("${device} : Action : Button 3 Pressed", "info")
			sendEvent(name: "pushed", value: 3, isStateChange: true)
			levelEvent(20,"increase")
			break

		case "brightness_move_up":
			logging("${device} : Action : Button 3 Held", "info")
			sendEvent(name: "held", value: 3, isStateChange: true)
			state.levelChangeStart = now()
			break

		case "brightness_step_down":
			logging("${device} : Action : Button 4 Pressed", "info")
			sendEvent(name: "pushed", value: 4, isStateChange: true)
			levelEvent(20,"decrease")
			break

		case "brightness_move_down":
			logging("${device} : Action : Button 4 Held", "info")
			sendEvent(name: "held", value: 4, isStateChange: true)
			state.levelChangeStart = now()
			break

		case "brightness_stop":

			int buttonNumber = device.currentState("held").value.toInteger()

			logging("${device} : Action : Button ${buttonNumber} Released", "info")
			sendEvent(name: "released", value: buttonNumber, isStateChange: true)

			if (buttonNumber == 3) {

				levelChange(100,"increase")

			} else {

				levelChange(100,"decrease")

			}

			break

		default:
			logging("${device} : Action : Type '$action' is an unknown action.", "warn")
			break

	}

	pauseExecution 200
	debounceActionParsing = false

}
