/*
 * 
 *  Zigbee2MQTT Device Driver
 *	
 */


@Field String driverVersion = "v1.06 (4th March 2025)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10
@Field String deviceName = "Zigbee2MQTT Device"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/zigbee2mqtt/drivers/zigbee2mqtt_device.groovy") {

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


void publish(String payload) {

	String ieee = getDataValue("ieee")
	parent.publish("$ieee", "$payload")

}


void processMQTT(def json) {

	checkDriver()

	sendEvent(name: "lqi", value: "${json.linkquality}".toInteger())
	String powerSource = "${json.device.powerSource}".toLowerCase().contains("mains") ? "mains" : "battery"
	sendEvent(name: "powerSource", value:"$powerSource")

	long installed = 0

	try {
		installed = Long.valueOf(getDataValue("installed"))
	} catch (Exception e) {
		logging("${device} : processMQTT : Installation still in progress.", "trace")
	}

	long millis = now()

	if (installed > 0 && millis - installed > 10000) {
	// We have to check because the first burst of messages show the building of the device.
	// Multiple relay devices will build one relay at a time, meaning we end up with duplicate children.

		def child

		if ("${json}".indexOf('state_l') >= 0) {

			int relays = "${json}".count('state_l')
			logging("${device} : Device has ${relays} switches.", "debug")

			for (i in 1..relays) {

				logging("${device} : Processing switch $i.", "debug")

				child = fetchChild("BirdsLikeWires", "Zigbee2MQTT Nested Switch", "$relays-$i")
				child.processMQTT(json)

			}

		} else if (json.containsKey('state')) {

			logging("${device} : Device has 1 switch.", "debug")

			child = fetchChild("BirdsLikeWires", "Zigbee2MQTT Nested Switch", "1-1")
			child.processMQTT(json)

		}

	}

	// NOTE FOR FUTURE ME
	/// Use 'if (json.update.containsKey('state'))' if checking for nested keys.

	device.name = "${json.device.model}"
	device.label = "${json.device.friendlyName}"

	updateDataValue("ieee", "${json.device.ieeeAddr}")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : processMQTT : ${json}", "trace")

	updateHealthStatus()

}
