/*
 * 
 *  Zigbee2MQTT Nested Switch Driver
 *	
 */


@Field String driverVersion = "v1.05 (3rd March 2025)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10
@Field String deviceName = "Zigbee2MQTT Nested Switch"


metadata {

	definition (name: "${deviceName}", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/zigbee2mqtt/drivers/zigbee2mqtt_nested_switch.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "Switch"

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

	updateDataValue("isComponent", "false")

	removeDataValue("label")
	removeDataValue("name")

}


void updateSpecifics() {

	return

}


def getStateType() {

	def details = "${device.deviceNetworkId}".split('-')
	String stateType = ("${details[-2]}" > 1) ? "state_l${details[-1]}" : "state"
	return stateType

}


void off() {

	String stateType = getStateType()
	parent.publish("\"${stateType}\":\"off\"")

}


void on() {

	String stateType = getStateType()
	parent.publish("\"${stateType}\":\"on\"")

}


void processMQTT(def json) {

	checkDriver()

	String stateType = getStateType()

	String switchState = json."$stateType".toLowerCase()
	sendEvent(name: "switch", value: "$switchState")

	String capSwitchState = switchState.capitalize()
	logging("${device} : Switch : $capSwitchState", "info")

	if ("${device.name}" != "${deviceName}") device.name = "${deviceName}"

	updateHealthStatus()

}
