/*
 * 
 *  Zigbee2MQTT Routing Driver
 *	
 */


@Field String driverVersion = "v1.03 (17th March 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 1


metadata {

	definition (name: "Zigbee2MQTT", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/general/drivers/zigbee2mqtt.groovy") {

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


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.library

	device.name = "Zigbee2MQTT"

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


void parse(String description) {

	updatePresence()
	checkDriver()

	try {

		def msg = interfaces.mqtt.parseMessage(description)

		if (msg.topic.indexOf('bridge') >= 0) {

			logging("${device} : parse() : Bridge message received.", "trace")
			return

		} else if (msg.topic.indexOf('/availability') >= 0) {

			logging("${device} : parse() : Availability message received.", "trace")
			return			

		} else {

			logging("${device} : Topic : ${msg.topic}", "debug")

			if ("${msg.payload}" != "") {

				logging("${device} : Payload : ${msg.payload}", "debug")

				if ("${msg.payload.charAt(0)}" == "{") {

					def json = new groovy.json.JsonSlurper().parseText(msg.payload)
					def device

					// Here we determine which driver to use based upon the model.
					switch("${json.device.model}") {

						case "E1744":
							device = fetchChild("BirdsLikeWires","IKEA Symfonisk Sound Controller","${json.device.networkAddress}")
							break						

						case "E1766":
						case "E1812":
							device = fetchChild("BirdsLikeWires","IKEA Tradfri Button","${json.device.networkAddress}")
							break

						case "WXKG06LM":
						case "WXKG07LM":
							device = fetchChild("BirdsLikeWires","Xiaomi Aqara Wireless Remote Switch","${json.device.networkAddress}")
							break

						case "WXKG11LM":
						case "WXKG12LM":
							device = fetchChild("BirdsLikeWires","Xiaomi Aqara Wireless Mini Switch","${json.device.networkAddress}")
							break

						default:		
							logging("Zigbee2MQTT : No known driver for the ${json.device.model} from ${json.device.manufacturerName}.", "warn")
							return

					}

					// Hand off the payload.
					device.processMQTT(json)

				} else {

					logging("${device} : Payload : Contained something other than JSON.", "debug")
					return

				}

			}

		}

	} catch (Exception e) {

		logging("${device} : ${e.message}.", "error")

	}

}
