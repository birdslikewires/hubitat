/*
 * 
 *  Zigbee2MQTT Nested Switch Driver
 *	
 */


@Field String driverVersion = "v1.08 (27th August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 50
@Field String deviceName = "Zigbee2MQTT Nested Switch"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/zigbee2mqtt/drivers/zigbee2mqtt_nested_switch.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "Switch"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void configureSpecifics() {

	updateDataValue("isComponent", "false")
	removeDataValue("label")
	removeDataValue("name")

}


void updateSpecifics() {

	return

}


void off() {

	String stateType = mqttGetStateType()
	parent.publish("\"${stateType}\":\"off\"")

}


void on() {

	String stateType = mqttGetStateType()
	parent.publish("\"${stateType}\":\"on\"")

}


void processMQTT(def json) {

	checkDriver()

	String stateType = mqttGetStateType()

	String switchState = json."$stateType".toLowerCase()
	sendEvent(name: "switch", value: "$switchState")

	String capSwitchState = switchState.capitalize()
	logging("${device} : Switch : $capSwitchState", "info")

	device.name = "${deviceName}"

	updateHealthStatus()

}
