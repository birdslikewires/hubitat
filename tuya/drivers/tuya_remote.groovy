/*
 * 
 *  Tuya Remote Driver
 *	
 */


@Field String driverVersion = "v1.02 (18th August 2021)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "Tuya Remote", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/tuya/drivers/tuya_remote.groovy") {

		capability "Configuration"
		capability "HoldableButton"
		capability "Initialize"
		capability "Momentary"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "ReleasableButton"

	}

	if (debugMode) {
		command "checkPresence"
		command "testCommand"
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

	sendEvent(name: "battery", value:"${json.battery}", unit: "%")

	switch("${json.device.model}") {

		case "E1766":
			sendEvent(name: "numberOfButtons", value: 2, isStateChange: false)
			break

		case "E1812":
			sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)
			break

	}

	String deviceName = "IKEA Tradfri Button ${json.device.model}"
	if ("${device.name}" != "$deviceName") device.name = "$deviceName"
	if ("${device.label}" != "${json.device.friendlyName}") device.label = "${json.device.friendlyName}"

	updateDataValue("encoding", "MQTT")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : parseMQTT : ${json}", "debug")

	updatePresence()
	checkDriver()

}
