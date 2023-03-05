/*
 * 
 *  Network UPS Tools MQTT Driver v1.03 (5th March 2023)
 *	
 */


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 1


metadata {

	definition (name: "Network UPS Tools MQTT", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/nut/drivers/nut_mqtt.groovy") {

		capability "PresenceSensor"

		command "disconnect"

		if (debugMode) {

			command "checkPresence"
			command "testCommand"

		}

	}

}


preferences {

	input name: "mqttBroker", type: "text", title: "Local MQTT Broker Address:", required: true
	input name: "mqttUser", type: "text", title: "MQTT Username:", required: false
	input name: "mqttPass", type: "password", title: "MQTT Password:", required: false

	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false

}


void testCommand() {

	logging("${device} : Test Command", "info")

}


void installed() {

	// Runs after first installation.
	logging("${device} : Installed", "info")
	configure()

}


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.library

	device.name = "Network UPS Tools MQTT"

}


void updateSpecifics() {
	// Called by library updated() method.

	disconnect()

	state.mqttBroker = settings?.mqttBroker
	state.mqttTopic = "ups/#"

	schedule("0/10 * * * * ? *", mqttConnect)

}


void disconnect() {
	// Disconnect from broker.

	interfaces.mqtt.disconnect()
	logging("${device} : MQTT : Disconnected from broker ${state.mqttBroker} (${state.mqttTopic}).", "info")

}


void uninstalled() {
	// Runs when device is removed.

	interfaces.mqtt.disconnect()
	logging("${device} : Uninstalled", "info")

}


void parse(String description) {

	updatePresence()

	try {

		def msg = interfaces.mqtt.parseMessage(description)

		logging("${device} : Topic : ${msg.topic}", "trace")
		logging("${device} : Payload : ${msg.payload}", "trace")

		String upsName = msg.topic.substring(msg.topic.lastIndexOf("/") + 1)
		def ups = fetchChild("BirdsLikeWires","Network UPS Tools MQTT Device","$upsName")

		def json = new groovy.json.JsonSlurper().parseText(msg.payload)
		
		logging("${device} : JSON : ${json}", "debug")

		// Do the battery stuff here. Battery voltage to percentage should be done in the main library, but I'm too tired to do that now.

		ups.parse([[name: "frequency", value: json.inputfrequency]])
		ups.parse([[name: "voltage", value: json.inputvoltage]])

		if (json.upsstatus.indexOf('OL') >= 0) {

			ups.parse([[name: "powerSource", value: "mains"]])

		} else {

			ups.parse([[name: "powerSource", value: "battery"]])

		}

	} catch (Exception e) {

		logging("${device} : Parse : ${e.message}", "error")

	}

}
