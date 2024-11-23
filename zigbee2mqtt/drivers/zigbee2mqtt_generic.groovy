/*
 * 
 *  Zigbee2MQTT Generic Device Driver
 *	
 */


@Field String driverVersion = "v1.00 (23rd November 2024)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "Zigbee2MQTT Generic Device", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/zigbee2mqtt/drivers/zigbee2mqtt_generic.groovy") {

		capability "Configuration"
		capability "PowerSource"
		capability "SignalStrength"

		attribute "healthStatus", "enum", ["offline", "online"]

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

	updateDataValue("encoding", "MQTT")
	updateDataValue("isComponent", "false")

	removeDataValue("ieeeAddr")
	removeDataValue("label")
	removeDataValue("name")

}


void updateSpecifics() {

	return

}


void refresh() {

	return

}


void publish(String endpoint, String cmd) {

	String ieee = getDataValue("ieee")
	parent.publish("$ieee", "$endpoint", "$cmd")

}


void processMQTT(def json) {

	updateHealthStatus()
	checkDriver()

	sendEvent(name: "lqi", value: "${json.linkquality}".toInteger())
	String powerSource = "${json.device.powerSource}".toLowerCase().contains("mains") ? "mains" : "battery"
	sendEvent(name: "powerSource", value:"$powerSource")

	def child

	if ("${json}".indexOf('state_l') >= 0) {

		int relays = "${json}".count('state_l')
		logging("${device} : Device has ${relays} switches.", "debug")

		for (i in 1..relays) {

			logging("${device} : Processing switch $i.", "debug")

			child = fetchChild("BirdsLikeWires", "Zigbee2MQTT Nested Child Switch", "$i")
			child.processMQTT(json)

		}

	} else if (json.containsKey('state')) {

		logging("${device} : Device has 1 switch.", "debug")

		child = fetchChild("BirdsLikeWires", "Zigbee2MQTT Nested Child Switch", "$i")
		child.processMQTT(json)

	}

	// NOTE FOR FUTURE ME
	/// Use 'if (json.update.containsKey('state'))' if checking for nested keys.

	if ("${device.name}" != "${json.device.model}") device.name = "${json.device.model}"
	if ("${device.label}" != "${json.device.friendlyName}") device.label = "${json.device.friendlyName}"

	updateDataValue("ieee", "${json.device.ieeeAddr}")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : processMQTT : ${json}", "trace")

}
