/*
 * 
 *  IKEA Tradfri Button Driver
 *	
 */


@Field String driverVersion = "v1.12 (14th March 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "IKEA Tradfri Button", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/ikea/drivers/ikea_tradfri_shortcut_button.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "SwitchLevel"

		attribute "batteryState", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0009,0020,1000,FC7C", outClusters: "0003,0004,0006,0008,0019,0102,1000", manufacturer: "IKEA of Sweden", model: "TRADFRI open/close remote", deviceJoinName: "IKEA Tradfri Open/Close Remote"
		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0009,0020,1000", outClusters: "0003,0004,0006,0008,0019,0102,1000", manufacturer: "IKEA of Sweden", model: "TRADFRI SHORTCUT Button", deviceJoinName: "IKEA Tradfri Shortcut Button"
		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0009,0020,1000,FC7C", outClusters: "0003,0004,0006,0008,0019,1000", manufacturer: "IKEA of Sweden", model: "TRADFRI SHORTCUT Button", deviceJoinName: "IKEA Tradfri Shortcut Button"

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

	// GOT SOME WORK TO DO HERE
	// How do you know that this driver is being used for Zigbee and not MQTT?

	int reportIntervalSeconds = reportIntervalMinutes * 60
	sendZigbeeCommands(zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, reportIntervalSeconds, reportIntervalSeconds, 0x00))   // Report in regardless of other changes.

	requestBasic()

}


void updateSpecifics() {

	return

}


// void refresh() {
	
// 	// Battery status can be requested if command is sent within about 3 seconds of an actuation.
// 	sendZigbeeCommands(zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020))
// 	logging("${device} : Refreshed", "info")

// }



void parse(String description) {

	updatePresence()

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

		if (map.attrId == "0020") {

			reportBattery("${map.value}", 10, 2.1, 3.0)

		} else {

			logging("${device} : Skipped : Power Cluster with no data.", "debug")

		}

	} else if (map.clusterId == "0001") {

		logging("${device} : Skipped : Power Cluster with no data.", "debug")

	} else if (map.clusterId == "0006") {
		// Tap and double-tap from the E1812

		if (receivedData.length == 0) {

			debouncePress(map)

		} else {

			logging("${device} : Skipped : On/Off Cluster with extraneous data.", "debug")

		}

	} else if (map.clusterId == "0008") {
		// Hold and release from the E1812

		if (map.command == "05" || map.command == "07") {

			debouncePress(map)

		} else {

			filterThis(map)

		}

	} else if (map.clusterId == "0102") {
		// Taps and releases from the E1766 (maybe others).
		// I don't have one, so I can't test in person.

		if (map.command == "00" || map.command == "01" || map.command == "02") {

			debouncePress(map)

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}

}


@Field static Boolean isParsing = false
def debouncePress(Map map) {

	if (isParsing) return
	isParsing = true

	if (map.clusterId == "0006") { 

		if (map.command == "01") {

			logging("${device} : Action : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else if (map.command == "00") {

			logging("${device} : Action : Button Double-Pressed", "info")
			sendEvent(name:"doubleTapped", value: 1, isStateChange:true)

		}

	} else if (map.clusterId == "0008") {

		if (map.command == "05") {

			logging("${device} : Action : Button Held", "info")
			sendEvent(name: "held", value: 1, isStateChange: true)

		} else if (map.command == "07") {

			logging("${device} : Action : Button Released", "info")
			sendEvent(name: "released", value: 1, isStateChange: true)

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "0102") {

		if (map.command == "00") {

			sendEvent(name: "pushed", value: 1, isStateChange: true)
			logging("${device} : Action : Button 1 Pressed", "info")

		} else if (map.command == "01") {

			sendEvent(name: "pushed", value: 2, isStateChange: true)
			logging("${device} : Action : Button 2 Pressed", "info")

		} else if (map.command == "02") {

			int whichButton = device.currentState("pushed").value.toInteger()
			sendEvent(name: "released", value: whichButton, isStateChange: true)
			logging("${device} : Action : Button $whichButton Released", "info")

		} else {

			reportToDev(map)

		}

	}

	pauseExecution 200
	isParsing = false

}


void processMQTT(def json) {

	// Process the action first!
	if (json.action) debounceAction("${json.action}")

	sendEvent(name: "battery", value:"${json.battery}", unit: "%")

	switch("${json.device.model}") {

		case "E1766":
			sendEvent(name: "numberOfButtons", value: 2, isStateChange: false)
			break

		case "E1812":
			sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)
			break

	}

	String deviceName = "IKEA Tradfri Button ${json.device.model}"
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

		case "on":
			logging("${device} : Action : Button 1 Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			break

		case "off":
			logging("${device} : Action : Button 1 Double Pressed", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
			break

		case "open":
			state.changeLevelStart = now()
			logging("${device} : Action : Button 1 Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			break

		case "close":
			logging("${device} : Action : Button 2 Pressed", "info")
			sendEvent(name: "pushed", value: 2, isStateChange: true)
			break

		case "stop":
			logging("${device} : Action : Button 1 Released", "info")
			sendEvent(name: "released", value: 1, isStateChange: true)
			levelChange(140)
			break

		case "brightness_move_up":
			state.changeLevelStart = now()
			logging("${device} : Action : Button 1 Held", "info")
			sendEvent(name: "held", value: 1, isStateChange: true)
			break

		case "brightness_stop":
			logging("${device} : Action : Button 1 Released", "info")
			sendEvent(name: "released", value: 1, isStateChange: true)
			levelChange(180)
			break

		default:
			logging("${device} : Action : Type '$action' is an unknown action.", "warn")
			break

	}

	pauseExecution 200
	debounceActionParsing = false

}
