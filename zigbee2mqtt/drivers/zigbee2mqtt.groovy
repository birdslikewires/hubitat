/*
 * 
 *  Zigbee2MQTT Routing Driver
 *	
 */


@Field String driverVersion = "v2.09 (20th August 2025)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 1
@Field String deviceName = "Zigbee2MQTT"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/zigbee2mqtt/drivers/zigbee2mqtt.groovy") {

		attribute "healthStatus", "enum", ["offline", "online"]

		command "disconnect"

		if (debugMode) {
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


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.library

	device.name = "$deviceName"

	removeDataValue("encoding")

}


void updateSpecifics() {
	// Called by library updated() method.

	disconnect()

	state.mqttBroker = settings?.mqttBroker
	state.mqttTopic = "zigbee2mqtt/#"

	schedule("0/10 * * * * ? *", mqttConnect)

}


void disconnect() {
	// Disconnect from broker.

	interfaces.mqtt.disconnect()
	logging("${device} : MQTT : Disconnected from broker ${state.mqttBroker} (${state.mqttTopic}).", "info")

}


void uninstalled() {
	// Runs when device is removed.

	disconnect()
	logging("${device} : Uninstalled", "info")

}


void publish(String ieee, String payload) {
	// Publishes an MQTT message.

	interfaces.mqtt.publish("zigbee2mqtt/$ieee/set","{$payload}")

}


void publishMQTT(String ieee, String getSetGo, String payload) {
	// Publishes an MQTT message.

	interfaces.mqtt.publish("zigbee2mqtt/$ieee/$getSetGo","$payload")

}


void parse(String description) {

	updateHealthStatus()
	checkDriver()

	try {

		def msg = interfaces.mqtt.parseMessage(description)

		if (msg.topic.indexOf('bridge') >= 0) {

			logging("${device} : parse() : Ignoring bridge message.", "trace")
			return

		} else if (msg.topic.indexOf('/availability') >= 0) {

			logging("${device} : parse() : Ignoring availability message.", "trace")
			return

		} else if (msg.topic.endsWith('/set')) {

			logging("${device} : parse() : Ignoring command message.", "trace")
			return		

		} else {

			logging("${device} : Topic : ${msg.topic}", "debug")

			if ("${msg.payload}" != "") {

				logging("${device} : Payload : ${msg.payload}", "trace")

				if ("${msg.payload.charAt(0)}" == "{") {

					def json = new groovy.json.JsonSlurper().parseText(msg.payload)

					if ("${json.device.type}" == "Unknown" ) {

						logging("${device} : Ignoring device at address ${json.device.ieeeAddr} due to unknown type.", "warn")
						return

					}

					def child = fetchChild("BirdsLikeWires","Zigbee2MQTT Device","${json.device.ieeeAddr}")
					child.processMQTT(json)

				} else {

					logging("${device} : Payload : Contained something that we can't interpret.", "debug")
					return

				}

			}

		}

	} catch (Exception e) {

		logging("${device} : ${e.message}.", "error")

	}

}
