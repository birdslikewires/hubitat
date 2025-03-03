/*
 * 
 *  Zigbee2MQTT Nested Dimmer Driver
 *	
 */


@Field String driverVersion = "v1.01 (3rd March 2025)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10
@Field String deviceName = "Zigbee2MQTT Nested Dimmer"


metadata {

	definition (name: "${deviceName}", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/zigbee2mqtt/drivers/zigbee2mqtt_nested_dimmer.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "Light"
		capability "Switch"
		capability "SwitchLevel"

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


void setLevel(BigDecimal pct) {

	setLevel(pct,1)

}


void setLevel(BigDecimal pct, BigDecimal duration) {

	Integer level = Math.round(pct * 2.54).toInteger()

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Got level request of '${pct}%' [${level}] over ${duration} second${pluralisor} [${duration}].", "debug")

	parent.publish("\"brightness\":${level},\"transition\":${duration}")

}


void processMQTT(def json) {

	checkDriver()

	String stateType = getStateType()

	String switchState = json."$stateType".toLowerCase()
	sendEvent(name: "switch", value: "$switchState")

	Integer currentLevel = json.brightness
	currentLevel = Math.round(currentLevel / 2.54).toInteger()
	sendEvent(name: "level", value: "${currentLevel}")

	String capSwitchState = switchState.capitalize()
	logging("${device} : Switch : $capSwitchState at $currentLevel%", "info")

	if ("${device.name}" != "${deviceName}") device.name = "${deviceName}"

	updateHealthStatus()

}
