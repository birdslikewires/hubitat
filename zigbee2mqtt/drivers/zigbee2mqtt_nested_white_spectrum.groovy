/*
 * 
 *  Zigbee2MQTT Nested White Spectrum Driver
 *	
 */


@Field String driverVersion = "v1.02 (4th March 2025)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10
@Field String deviceName = "Zigbee2MQTT Nested White Spectrum"


metadata {

	definition (name: "${deviceName}", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/zigbee2mqtt/drivers/zigbee2mqtt_nested_white_spectrum.groovy") {

		capability "Actuator"
		capability "ColorTemperature"
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

	sendEvent(name: "colorMode", value: "CT", isStateChange: false)

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


void setLevel(BigDecimal pc) {

	setLevel(pc,1)

}


void setLevel(BigDecimal pc, BigDecimal duration) {

	Integer level = percentageToOctet(pc.toInteger())

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Got level request of ${pc}% [${level}] over ${duration} second${pluralisor} [${duration}].", "debug")

	parent.publish("\"brightness\":${level},\"transition\":${duration}")

}


void setColorTemperature(BigDecimal kelvin) {

	Integer initialLevel = device.currentState("level").value.toInteger()
	setColorTemperature(kelvin,initialLevel,0)

}


void setColorTemperature(BigDecimal kelvin, BigDecimal pc, BigDecimal duration) {

	Integer mired = kelvinToMired(kelvin.toInteger())
	Integer level = percentageToOctet(pc.toInteger())

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setColorTemperature : Got colour request of ${kelvin} Kelvin [${mired}] at ${pc}% [${level}] over ${duration} second${pluralisor} [${duration}].", "debug")

	parent.publish("\"brightness\":${level},\"color_temp\":${mired},\"transition\":${duration}")

}


void processMQTT(def json) {

	checkDriver()

	String stateType = mqttGetStateType()

	String switchState = json."$stateType".toLowerCase()
	sendEvent(name: "switch", value: "$switchState")

	Integer currentLevel = json.brightness
	currentLevel = octetToPercentage(currentLevel)
	sendEvent(name: "level", value: "${currentLevel}")

	Integer colourTemperature = json.color_temp
	colourTemperature = miredToKelvin(colourTemperature)
	sendEvent(name: "colorTemperature", value: "${colourTemperature}")

	String capSwitchState = switchState.capitalize()
	logging("${device} : Switch : $capSwitchState at $currentLevel%", "debug")

	device.name = "${deviceName}"
	updateHealthStatus()

}
