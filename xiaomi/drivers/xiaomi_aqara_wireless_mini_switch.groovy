/*
 * 
 *  Xiaomi Aqara Wireless Mini Switch Driver
 *	
 */


@Field String driverVersion = "v1.22 (21st August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 50
@Field String deviceName = "Xiaomi Aqara Wireless Mini Switch"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/xiaomi/drivers/xiaomi_aqara_wireless_mini_switch.groovy") {

		capability "AccelerationSensor"
		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "SwitchLevel"
		capability "VoltageMeasurement"

		attribute "action", "string"
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

	removeDataValue("isComponent")
	removeDataValue("label")
	removeDataValue("name")

}


void updateSpecifics() {

	return

}


void accelerationInactive() {

	sendEvent(name: "acceleration", value: "inactive", isStateChange: true)

}



void processMQTT(def json) {

	checkDriver()

	// Tasks

	if (json.action) {

		withDebounce("${json.device.networkAddress}", 200, {

			switch("${json.action}") {

				case "single":
					logging("${device} : Action : Button 1 Pressed", "info")
					sendEvent(name: "pushed", value: 1, isStateChange: true)
					break

				case "double":
					logging("${device} : Action : Button 2 Pressed", "info")
					sendEvent(name: "pushed", value: 2, isStateChange: true)
					logging("${device} : Action : Button Double Tapped", "info")
					sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
					break

				case "triple":
					logging("${device} : Action : Button 3 Pressed", "info")
					sendEvent(name: "pushed", value: 3, isStateChange: true)
					break

				case "quadruple":
					logging("${device} : Action : Button 4 Pressed", "info")
					sendEvent(name: "pushed", value: 4, isStateChange: true)
					break

				case "hold":
					state.levelChangeStart = now()
					logging("${device} : Action : Button Held", "info")
					sendEvent(name: "held", value: 1, isStateChange: true)
					sendEvent(name: "pushed", value: 3, isStateChange: true)
					break

				case "release":
					logging("${device} : Action : Button Released", "info")
					sendEvent(name: "released", value: 1, isStateChange: true)
					sendEvent(name: "pushed", value: 4, isStateChange: true)
					levelChange(160)
					break

				case "shake":
					logging("${device} : Action : Button Shaken", "info")
					sendEvent(name: "acceleration", value: "active", isStateChange: true)
					sendEvent(name: "pushed", value: 5, isStateChange: true)
					runIn(4,accelerationInactive)
					break

				default:
					logging("${device} : Action : '${json.action}' is an unknown action.", "warn")
					break

			}

			sendEvent(name: "action", value: "${json.action}", isStateChange: true)

		})

	}

	// Admin

	sendEvent(name: "battery", value:"${json.battery}", unit: "%")

	BigDecimal batteryVoltage = new BigDecimal(json.voltage)
	batteryVoltage = batteryVoltage / 1000
	batteryVoltage = batteryVoltage.setScale(3, BigDecimal.ROUND_HALF_UP)
	sendEvent(name: "voltage", value: batteryVoltage, unit: "V")	

	switch("${json.device.model}") {

		case "WXKG11LM":
			sendEvent(name: "numberOfButtons", value: 4, isStateChange: false)
			break

		case "WXKG12LM":
			sendEvent(name: "numberOfButtons", value: 5, isStateChange: false)
			break

	}

	String deviceNameFull = "$deviceName ${json.device.model}"
	if ("${device.name}" != "$deviceNameFull") device.name = "$deviceNameFull"
	if ("${device.label}" != "${json.device.friendlyName}") device.label = "${json.device.friendlyName}"

	updateDataValue("encoding", "MQTT")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : parseMQTT : ${json}", "debug")

	updateHealthStatus()

}
