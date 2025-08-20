/*
 * 
 *  IKEA Symfonisk Sound Controller Driver
 *	
 */


@Field String driverVersion = "v1.11 (20th August 2025)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 50
@Field String deviceName = "IKEA Symfonisk Sound Controller"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/ikea/drivers/ikea_symfonisk_sound_controller.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "Momentary"
		capability "PushableButton"
		capability "Refresh"
		capability "ReleasableButton"
		capability "SwitchLevel"

		attribute "batteryState", "string"
		attribute "direction", "string"
		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "levelChange", "integer"

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

	String encodingCheck = "${getDeviceDataByName('encoding')}"

	if (encodingCheck.indexOf('MQTT') >= 0) {

		logging("${device} : configureSpecifics() : MQTT device, no Zigbee configuration required.", "trace")

	} else {

		int reportIntervalSeconds = reportIntervalMinutes * 60
		sendZigbeeCommands(zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, DataType.UINT8, reportIntervalSeconds, reportIntervalSeconds, 0x00))   // Report in regardless of other changes.
		requestBasic()

	}

	sendEvent(name: "level", value: 0, isStateChange: false)

}


void updateSpecifics() {

	return

}


void refresh() {

	// Battery status can be requested if command is sent within about 3 seconds of an actuation.
	sendZigbeeCommands(zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021))
	logging("${device} : Refreshed", "info")

}


void parse(String description) {

	updateHealthStatus()
	checkDriver()
	
	logging("${device} : parse() : $description", "trace")

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		reportToDev(descriptionMap)

	}

}


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedData = map.data

	if (map.cluster == "0001") { 
		// Power Configuration Cluster

		if (map.attrId == "0021") {

			reportBattery("${map.value}", 10, 2.1, 3.0)

		} else {

			logging("${device} : Skipped : Power Cluster with no data.", "debug")

		}

	} else if (map.clusterId == "0001") { 

		logging("${device} : Skipped : Power Cluster with no data.", "debug")

	} else if (map.clusterId == "0006") { 

		if (map.command == "02") {

			debouncePress(map)

		} else {

			logging("${device} : Skipped : On/Off Cluster with extraneous data.", "debug")

		}

	} else if (map.clusterId == "0008") {

		debouncePress(map)

	} else {

		filterThis(map)

	}

}


@Field static Boolean isParsing = false
def debouncePress(Map map) {

	if (isParsing) return
	isParsing = true

	// We'll figure out the button numbers in a tick.
	int buttonNumber = 0

	if (map.clusterId == "0006") { 

		// This is a press of the button.
		buttonNumber = 1

		logging("${device} : Action : Button ${buttonNumber} Pressed", "info")
		sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
		
		device.currentState("switch").value == "off" ? on() : off()

	} else if (map.clusterId == "0008") {

		if (map.command == "01") {

			// This is a turn of the dial starting.
			buttonNumber = 2

			String[] receivedData = map.data

			if (receivedData[0] == "00") {

				buttonNumber = 2
				state.levelChangeStart = now()
				logging("${device} : Action : Dial (Button ${buttonNumber}) Turning Clockwise", "info")
				sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
				sendEvent(name: "direction", value: "clockwise")
				sendEvent(name: "held", value: buttonNumber, isStateChange: true)
				sendEvent(name: "switch", value: "on", isStateChange: false)

			} else if (receivedData[0] == "01") {

				buttonNumber = 3
				state.levelChangeStart = now()
				logging("${device} : Action : Dial (Button ${buttonNumber}) Turning Anticlockwise", "info")
				sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
				sendEvent(name: "direction", value: "anticlockwise")
				sendEvent(name: "held", value: buttonNumber, isStateChange: true)
				sendEvent(name: "switch", value: "on", isStateChange: false)

			} else {

				reportToDev(map)

			}

		} else if (map.command == "02") {

			// This is a multi-press of the button.
			buttonNumber = 1

			String[] receivedData = map.data

			if (receivedData[0] == "00") {

				// Double-press is a supported Hubitat action, so just report the event.
				logging("${device} : Action : Button ${buttonNumber} Double Pressed", "info")
				sendEvent(name: "doubleTapped", value: buttonNumber, isStateChange: true)

			} else if (receivedData[0] == "01") {

				// Triple-pressing is not a supported Hubitat action, but this device doesn't support hold or release on the button, so we'll use "held" for this.
				logging("${device} : Action : Button ${buttonNumber} Triple Pressed", "info")
				sendEvent(name: "held", value: buttonNumber, isStateChange: true)

			} else {

				reportToDev(map)

			}

		} else if (map.command == "03") {

			// This is a turn of the dial stopping.
			// There's no differentiation in the data sent, so we work out from which direction we're stopping using the previous hold state.

			buttonNumber = device.currentState("held").value.toInteger()

			logging("${device} : Action : Dial (Button ${buttonNumber}) Stopped", "info")
			sendEvent(name: "released", value: buttonNumber, isStateChange: true)

			// Now work out the level we should change to based upon the time spent changing.

			Integer initialLevel = device.currentState("level").value.toInteger()

			long millisTurning = now() - state.levelChangeStart
			if (millisTurning > 6000) {
				millisTurning = 0				// In case the messages don't stop we could end up at full brightness or VOLUME!
			}

			BigInteger levelChange = 0
			levelChange = millisTurning / 6000 * 100

			BigDecimal targetLevel = 0

			if (buttonNumber == 2) {

				targetLevel = device.currentState("level").value.toInteger() + levelChange

			} else {

				targetLevel = device.currentState("level").value.toInteger() - levelChange
				levelChange *= -1

			}

			logging("${device} : Level : Dial (Button ${buttonNumber}) - Changing from initialLevel '${initialLevel}' by levelChange '${levelChange}' after millisTurning for ${millisTurning} ms.", "debug")

			sendEvent(name: "levelChange", value: levelChange, isStateChange: true)

			setLevel(targetLevel)

		} else {

			reportToDev(map)

		}

	}

	pauseExecution 110
	isParsing = false

}



void processMQTT(def json) {

	// Process the action first!
	if (json.action) debounceAction("${json.action}")

	sendEvent(name: "battery", value:"${json.battery}", unit: "%")
	sendEvent(name: "numberOfButtons", value: 3, isStateChange: false)
	
	String deviceName = "Symfonisk Sound Controller E1744"
	if ("${device.name}" != "$deviceName") device.name = "$deviceName"
	if ("${device.label}" != "${json.device.friendlyName}") device.label = "${json.device.friendlyName}"

	updateDataValue("encoding", "MQTT")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : parseMQTT : ${json}", "debug")

	updateHealthStatus()
	checkDriver()

}


@Field static Boolean debounceActionParsing = false
void debounceAction(String action) {

	if (debounceActionParsing) {
		logging("${device} : debounceAction : DEBOUNCED", "debug")
		return
	}
	debounceActionParsing = true

	switch(action) {

		case "toggle":
			logging("${device} : Action : Button 1 Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			break

		case "brightness_step_up":
			logging("${device} : Action : Button 1 Double Pressed", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
			break			

		case "brightness_step_down":
			logging("${device} : Action : Button 1 Triple Pressed", "info")
			sendEvent(name: "held", value: 1, isStateChange: true)
			break

		case "brightness_move_up":
			logging("${device} : Action : Button 2 Pressed", "info")
			sendEvent(name: "pushed", value: 2, isStateChange: true)
			sendEvent(name: "held", value: 2, isStateChange: true)
			state.levelChangeStart = now()
			break

		case "brightness_move_down":
			logging("${device} : Action : Button 3 Pressed", "info")
			sendEvent(name: "pushed", value: 3, isStateChange: true)
			sendEvent(name: "held", value: 3, isStateChange: true)
			state.levelChangeStart = now()
			break

		case "brightness_stop":

			int buttonNumber = device.currentState("held").value.toInteger()

			logging("${device} : Action : Button ${buttonNumber} Released", "info")
			sendEvent(name: "released", value: buttonNumber, isStateChange: true)

			if (buttonNumber == 2) {

				levelChange(100,"increase")

			} else {

				levelChange(100,"decrease")

			}

			break

		default:
			logging("${device} : Action : '$action' is an unknown action.", "info")
			break

	}

	pauseExecution 200
	debounceActionParsing = false

}
