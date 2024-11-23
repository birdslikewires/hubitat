/*
 * 
 *  Zigbee2MQTT Nested Child Switch Driver
 *	
 */


@Field String driverVersion = "v1.00 (23rd November 2024)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "Zigbee2MQTT Nested Child Switch", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/zigbee2mqtt/drivers/zigbee2mqtt_nested_child_switch.groovy") {

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


void refresh() {

	return

}


void off() {

	String switchNumber = "${device.deviceNetworkId}".split('-').last()
	parent.publish("state_l${switchNumber}", "off")

}


void on() {

	String switchNumber = "${device.deviceNetworkId}".split('-').last()
	parent.publish("state_l${switchNumber}", "on")

}


void processMQTT(def json) {

	checkDriver()

	String switchNumber = "${device.deviceNetworkId}".split('-').last()
	switchNumber = "state_l" + switchNumber

	String switchState = json."$switchNumber".toLowerCase()
	sendEvent(name: "switch", value: "$switchState")

	String capSwitchState = switchState.capitalize()
	logging("${device} : Switch : $capSwitchState", "info")

}
