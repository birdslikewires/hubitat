/*
 * 
 *  Xiaomi Aqara Wireless Remote Switch Driver
 *	
 */


@Field String driverVersion = "v1.22 (28th August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 50
@Field String deviceName = "Xiaomi Aqara Wireless Remote Switch"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/xiaomi/drivers/xiaomi_aqara_wireless_remote_switch.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "VoltageMeasurement"

		attribute "action", "string"
		attribute "healthStatus", "enum", ["offline", "online"]

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void configureSpecifics() {

	updateDataValue("encoding", "MQTT")

}


void updateSpecifics() {

	return

}


void processMQTT(def json) {

	checkDriver()

	// Tasks

	if (json.action) {

		withDebounce("${json.device.networkAddress}", 200, {

			switch("${json.action}") {

				case "single":
				case "single_left":
					logging("${device} : Action : Button 1 Pressed", "info")
					sendEvent(name: "pushed", value: 1, isStateChange: true)
					break

				case "double":
				case "double_left":
					logging("${device} : Action : Button 1 Double Pressed", "info")
					sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
					break

				case "hold":
				case "hold_left":
					logging("${device} : Action : Button 1 Held", "info")
					sendEvent(name: "held", value: 1, isStateChange: true)
					break

				case "single_right":
					logging("${device} : Action : Button 2 Pressed", "info")
					sendEvent(name: "pushed", value: 2, isStateChange: true)
					break

				case "double_right":
					logging("${device} : Action : Button 2 Double Pressed", "info")
					sendEvent(name: "doubleTapped", value: 2, isStateChange: true)
					break

				case "hold_right":
					logging("${device} : Action : Button 2 Held", "info")
					sendEvent(name: "held", value: 2, isStateChange: true)
					break

				case "single_both":
					logging("${device} : Action : Button 3 Pressed", "info")
					sendEvent(name: "pushed", value: 3, isStateChange: true)
					break

				case "double_both":
					logging("${device} : Action : Button 1 Double Pressed", "info")
					sendEvent(name: "doubleTapped", value: 3, isStateChange: true)
					break

				case "hold_both":
					logging("${device} : Action : Button 1 Held", "info")
					sendEvent(name: "held", value: 3, isStateChange: true)
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

		case "WXKG06LM":
			sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)
			break

		case "WXKG07LM":
			sendEvent(name: "numberOfButtons", value: 3, isStateChange: false)
			break

	}

	String deviceNameFull = "$deviceName ${json.device.model}"
	device.name = "$deviceNameFull"

	mqttProcessBasics(json)
	updateHealthStatus()

	logging("${device} : processMQTT : ${json}", "debug")

}
