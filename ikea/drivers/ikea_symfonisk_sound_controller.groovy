/*
 * 
 *  IKEA Symfonisk Sound Controller Driver
 *	
 */


@Field String driverVersion = "v1.08 (16th March 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "IKEA Symfonisk Sound Controller", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/ikea/drivers/ikea_symfonisk_sound_controller.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "Momentary"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Refresh"
		capability "ReleasableButton"
		capability "SwitchLevel"

		attribute "batteryState", "string"

		attribute "direction", "string"
		attribute "levelChange", "integer"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,1000", outClusters: "0003,0004,0006,0008,0019,1000", manufacturer: "IKEA of Sweden", model: "SYMFONISK Sound Controller", deviceJoinName: "IKEA Symfonisk Sound Controller", application: "21"
		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,1000,FC7C", outClusters: "0003,0004,0005,0006,0008,0019,1000", manufacturer: "IKEA of Sweden", model: "SYMFONISK Sound Controller", deviceJoinName: "IKEA Symfonisk Sound Controller", application: "21"

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

}


void refresh() {

	// Battery status can be requested if command is sent within about 3 seconds of an actuation.
	sendZigbeeCommands(zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021))
	logging("${device} : Refreshed", "info")

}


void parse(String description) {

	updatePresence()
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

		logging("${device} : Trigger : Button ${buttonNumber} Pressed", "info")
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
				logging("${device} : Trigger : Dial (Button ${buttonNumber}) Turning Clockwise", "info")
				sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
				sendEvent(name: "direction", value: "clockwise")
				sendEvent(name: "held", value: buttonNumber, isStateChange: true)
				sendEvent(name: "switch", value: "on", isStateChange: false)

			} else if (receivedData[0] == "01") {

				buttonNumber = 3
				state.levelChangeStart = now()
				logging("${device} : Trigger : Dial (Button ${buttonNumber}) Turning Anticlockwise", "info")
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
				logging("${device} : Trigger : Button ${buttonNumber} Double Pressed", "info")
				sendEvent(name: "doubleTapped", value: buttonNumber, isStateChange: true)

			} else if (receivedData[0] == "01") {

				// Triple-pressing is not a supported Hubitat action, but this device doesn't support hold or release on the button, so we'll use "held" for this.
				logging("${device} : Trigger : Button ${buttonNumber} Triple Pressed", "info")
				sendEvent(name: "held", value: buttonNumber, isStateChange: true)

			} else {

				reportToDev(map)

			}

		} else if (map.command == "03") {

			// This is a turn of the dial stopping.
			// There's no differentiation in the data sent, so we work out from which direction we're stopping using the previous hold state.

			buttonNumber = device.currentState("held").value.toInteger()

			logging("${device} : Trigger : Dial (Button ${buttonNumber}) Stopped", "info")
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

	String deviceName = "Xiaomi Aqara Wireless Mini Switch ${json.device.model}"
	if ("${device.name}" != "$deviceName") device.name = "$deviceName"
	if ("${device.label}" != "${json.device.friendlyName}") device.label = "${json.device.friendlyName}"

	updateDataValue("encoding", "MQTT")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

	logging("${device} : parseMQTT : ${json}", "debug")

	updatePresence()
	checkDriver()

}


@Field static Boolean debounceActionParsing = false
void debounceAction(String action) {

	if (debounceActionParsing) {
		logging("${device} : parseMQTT : DEBOUNCED", "debug")
		return
	}
	debounceActionParsing = true

	switch(action) {

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
			logging("${device} : Action : '$action' is an unknown action.", "info")
			break

	}

	pauseExecution 200
	debounceActionParsing = false

}
