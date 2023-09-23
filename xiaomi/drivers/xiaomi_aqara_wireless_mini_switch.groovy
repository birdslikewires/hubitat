/*
 * 
 *  Xiaomi Aqara Wireless Mini Switch Driver
 *	
 */


@Field String driverVersion = "v1.20 (26th August 2023)"


#include BirdsLikeWires.library
#include BirdsLikeWires.xiaomi
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "Xiaomi Aqara Wireless Mini Switch", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_aqara_wireless_mini_switch.groovy") {

		capability "AccelerationSensor"
		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "SwitchLevel"
		//capability "TemperatureMeasurement"	// Just because you can doesn't mean you should.
		capability "VoltageMeasurement"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,FFFF,0006", outClusters: "0000,0004,FFFF", manufacturer: "LUMI", model: "lumi.sensor_switch.aq2", deviceJoinName: "WXKG11LMr1"
		fingerprint profileId: "0104", inClusters: "0000,0012,0003", outClusters: "0000", manufacturer: "LUMI", model: "lumi.remote.b1acn01", deviceJoinName: "WXKG11LMr2"
		fingerprint profileId: "0104", inClusters: "0000,0012,0006,0001", outClusters: "0000", manufacturer: "LUMI", model: "lumi.sensor_switch.aq3", deviceJoinName: "WXKG12LM"
		fingerprint profileId: "0104", inClusters: "0000,0012,0006,0001", outClusters: "0000", manufacturer: "LUMI", model: "lumi.sensor_swit", deviceJoinName: "WXKG12LM"

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
	// Called by main configure() method in BirdsLikeWires.xiaomi

	String modelCheck = "${getDeviceDataByName('model')}"

	// Devices with models starting "lumi" are directly connected.

	if ("$modelCheck" == "lumi.sensor_switch.aq2") {
		// This is the WXKG11LM original 2015 model.

		updateDataValue("encoding", "Xiaomi")
		device.name = "Xiaomi Aqara Wireless Mini Switch WXKG11LMr1"
		sendEvent(name: "numberOfButtons", value: 5, isStateChange: false)
		device.deleteCurrentState("level")

	} else if ("$modelCheck" == "lumi.remote.b1acn01") {
		// This is the WXKG11LM revised 2018 model featuring hold and release.

		updateDataValue("encoding", "Xiaomi")
		device.name = "Xiaomi Aqara Wireless Mini Switch WXKG11LMr2"
		sendEvent(name: "numberOfButtons", value: 5, isStateChange: false)
		sendEvent(name: "level", value: 0, isStateChange: false)

	} else if ("$modelCheck" == "lumi.sensor_switch.aq3") {
		// This is the WXKG12LM with gyroscope for shake functionality.

		updateDataValue("encoding", "Xiaomi")
		device.name = "Xiaomi Aqara Wireless Mini Switch WXKG12LM"
		sendEvent(name: "numberOfButtons", value: 6, isStateChange: false)
		sendEvent(name: "level", value: 0, isStateChange: false)
		sendEvent(name: "acceleration", value: "inactive", isStateChange: false)

	} else if ("$modelCheck" == "lumi.sensor_swit") {
		// There's a weird truncation of the model string which only occurs with the '12LM. It looks like a firmware bug.

		updateDataValue("model", "lumi.sensor_switch.aq3")
		configureSpecifics()

	} else {

		String encodingCheck = "${getDeviceDataByName('encoding')}"

		if (encodingCheck.indexOf('MQTT') >= 0) {
			// If this is an MQTT device everything is already configured. Just tidy up.

			removeDataValue("isComponent")
			removeDataValue("label")
			removeDataValue("name")

		} else if (modelCheck.indexOf('FF42') >= 0) {
			// We may have a raw hex message in here. Mine looked like this:
			// 166C756D692E73656E736F725F7377697463682E61713201FF421A0121BD0B03281C0421A81305214C02062406000000000A2108C6
			
			// If this is a common thing we should move this to the Xiaomi library, but I've only ever seen it on these mini switches.

			modelCheck = modelCheck.split('FF42')[0]
			String extractedModel = hexToText("$modelCheck")

			if (extractedModel.indexOf('lumi.sensor_switch') >= 0) {

				updateDataValue("model", "$extractedModel")
				logging("${device} : Updated model name to '$extractedModel'.", "info")
				configureSpecifics()

			} else {

				logging("${device} : Extracted '$extractedModel' but this is an unknown device to this driver. Please report to developer.", "warn")

			}

		} else {

			logging("${device} : Model '$modelCheck' is not known.", "debug")

		}

	}

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void accelerationInactive() {

	sendEvent(name: "acceleration", value: "inactive", isStateChange: true)

}


void processMap(Map map) {

	if (map.cluster == "0006") { 
		// Handle button presses for the WXKG11LM 

		int buttonNumber = map.value[-1..-1].toInteger()
		buttonNumber = buttonNumber == 0 ? 1 : buttonNumber

		if (buttonNumber == 2) {
			logging("${device} : Action : Button Double Tapped", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
		}

		logging("${device} : Action : Button ${buttonNumber} Pressed", "info")
		sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

	} else if (map.cluster == "0012") { 
		// Handle button presses for the WXKG12LM and holds for WXKG11LMr2

		if (map.value == "0100") {

			logging("${device} : Action : Button 1 Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else if (map.value == "0200") {

			logging("${device} : Action : Button 2 Pressed", "info")
			sendEvent(name: "pushed", value: 2, isStateChange: true)
			logging("${device} : Action : Button Double Tapped", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)

		} else if (map.value == "0000" || map.value == "1000") {

			state.levelChangeStart = now()
			logging("${device} : Action : Button Held", "info")
			sendEvent(name: "held", value: 1, isStateChange: true)
			sendEvent(name: "pushed", value: 3, isStateChange: true)

		} else if (map.value == "1100" || map.value == "FF00") {

			logging("${device} : Action : Button Released", "info")
			sendEvent(name: "released", value: 1, isStateChange: true)
			sendEvent(name: "pushed", value: 4, isStateChange: true)
			levelChange(140)

		} else if (map.value == "1200") {

			logging("${device} : Action : Button Shaken", "info")
			sendEvent(name: "acceleration", value: "active", isStateChange: true)
			sendEvent(name: "pushed", value: 6, isStateChange: true)
			runIn(4,accelerationInactive)

		} else {

			filterThis(map)

		}

	} else if (map.cluster == "0000") { 

		if (map.attrId == "0005") {
			// Received when pairing and when short-pressing the reset button.

			if (map.size == "36") {
				// Model data only, usually received during pairing.
				logging("${device} : Model data received.", "debug")

			} else if (map.size == "70" || map.size == "88") {
				// Short reset button presses always contain battery data. <-- hmm, do they? because I'm seeing silly values.
				// Value size of 70 sent by '11LM, size of 88 by '12LM.
				//xiaomiDeviceStatus(map) <-- don't do this until confirmed, just read the regular reports.

				// Grab device data triggered by short press of the reset button.
				//deviceData = map.value.split('FF42')[1]

				// Scrounge more value! It's another button, so as '11LMs can quad-click we'll call this button five.
				logging("${device} : Action : Button 5 Pressed", "info")
				sendEvent(name: "pushed", value: 5, isStateChange: true)

			}

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}

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

	updateHealthStatus()
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
