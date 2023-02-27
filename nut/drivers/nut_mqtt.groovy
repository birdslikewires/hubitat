/*
 * 
 *  Network UPS Tools MQTT Driver v1.01 (27th February 2023)
 *	
 */


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 1


metadata {

	definition (name: "Network UPS Tools MQTT", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/nut/drivers/nut_mqtt.groovy") {

		capability "PresenceSensor"

		command "disconnect"

		if (debugMode) {

			capability "Initialize"

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

	// Set default preferences.
	device.updateSetting("infoLogging", [value: "true", type: "bool"])
	device.updateSetting("debugLogging", [value: "${debugMode}", type: "bool"])
	device.updateSetting("traceLogging", [value: "${debugMode}", type: "bool"])

	// Set device name.
	device.name = "Network UPS Tools MQTT"

	logging("${device} : Installed", "info")

	initialize()

}


void initialize() {

	int randomSixty
	String modelCheck = "${getDeviceDataByName('model')}"

	// Tidy up.
	unschedule()
	state.clear()
	state.presenceUpdated = 0	
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Schedule presence checking.
	int checkEveryMinutes = 1
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	sendEvent(name: "initialisation", value: "complete", isStateChange: false)
	logging("${device} : Initialised", "info")

}


void configure() {

	// Notify.
	sendEvent(name: "configuration", value: "complete", isStateChange: false)
	logging("${device} : Configuration complete.", "info")
	
}


void updateSpecifics() {
	// Called by library updated() method.

	disconnect()

	state.mqttBroker = settings?.mqttBroker
	state.mqttTopic = "ups/#"

	schedule("0/10 * * * * ? *", mqttConnect)

	configure()

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


void mqttConnect() {

	try {

		def mqttInt = interfaces.mqtt

		if (mqttInt.isConnected()) {
			logging("${device} : MQTT : Connection to broker ${state.mqttBroker} (${state.mqttTopic}) is live.", "trace")
			return
		}

		if (state.mqttTopic == "") {
			logging("${device} : MQTT : Topic is not set.", "error")
			return
		}

		String clientID = "hubitat-" + device.deviceNetworkId
		mqttBrokerUrl = "tcp://" + state.mqttBroker + ":1883"
		mqttInt.connect(mqttBrokerUrl, clientID, settings?.mqttUser, settings?.mqttPass)
		pauseExecution(500)
		mqttInt.subscribe(state.mqttTopic)

	} catch (Exception e) {

		logging("${device} : MQTT : ${e.message}", "error")

	}

} 


void mqttClientStatus(String status) {

	if (status.indexOf('Connection succeeded') >= 0) {

		logging("${device} : MQTT : Connection to broker ${state.mqttBroker} (${state.mqttTopic}) is live.", "trace")

	} else {

		logging("${device} : MQTT : ${status}", "error")

	}

}
