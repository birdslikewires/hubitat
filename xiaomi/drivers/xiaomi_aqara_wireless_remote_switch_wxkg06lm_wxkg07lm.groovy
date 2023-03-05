/*
 * 
 *  Xiaomi Aqara Wireless Remote Switch WXKG06LM / WXKG07LM Driver
 *	
 */


@Field String driverVersion = "v1.14 (1st March 2023)"


#include BirdsLikeWires.library
#include BirdsLikeWires.xiaomi
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "Xiaomi Aqara Wireless Remote Switch WXKG06LM / WXKG07LM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_aqara_wireless_remote_switch_wxkg06lm_wxkg07lm.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "VoltageMeasurement"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,0019,FFFF,0012", outClusters: "0000,0004,0003,0005,0019,FFFF,0012", manufacturer: "LUMI", model: "lumi.remote.b186acn02", deviceJoinName: "WXKG06LM", application: "09"
		fingerprint profileId: "0104", inClusters: "0000,0003,0019,FFFF,0012", outClusters: "0000,0004,0003,0005,0019,FFFF,0012", manufacturer: "LUMI", model: "lumi.remote.b286acn02", deviceJoinName: "WXKG07LM", application: "09"

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

	updateDataValue("encoding", "Xiaomi")

	String modelCheck = "${getDeviceDataByName('model')}"

	if (modelCheck.indexOf('lumi.remote.b186acn02') >= 0) {
		// This is the WXKG06LM single key device.

		device.name = "Xiaomi Aqara Wireless Remote Switch WXKG06LM"
		updateDataValue("name", "WXKG06LM")
		sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)

	} else if (modelCheck.indexOf('lumi.remote.b286acn02') >= 0) {
		// This is the WXKG07LM double key device.

		device.name = "Xiaomi Aqara Wireless Remote Switch WXKG07LM"
		updateDataValue("name", "WXKG07LM")
		sendEvent(name: "numberOfButtons", value: 3, isStateChange: false)

	} else {

		logging("${device} : Model '$modelCheck' is not known.", "warn")

	}

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void deviceDetails(int buttonNumber) {

	String deviceName = "${getDeviceDataByName('name')}"

	if (deviceName == "null") {
		// We know that device details are incomplete.

		String modelCheck = "${getDeviceDataByName('model')}"

		if (modelCheck == "null") {
			// If both are null then configureSpecifics() will never fix this. Intervene!

			if (buttonNumber > 1) {
				// More than one button can only be an '07LM.

				updateDataValue("manufacturer", "LUMI")
				updateDataValue("model", "lumi.remote.b286acn02")
				configureSpecifics()

			} else {
				// This could be either model. Until we know more we assume it's an '06LM.

				updateDataValue("manufacturer", "LUMI")
				updateDataValue("model", "lumi.remote.b186acn02")
				configureSpecifics()

			}

		}

	} else {
		// We have a device name already.

		if (deviceName.indexOf('WXKG06LM') >= 0) {
			// But is it correct?

			if (buttonNumber > 1) {
				// Not if we're getting these messages from a single button device!

				updateDataValue("model", "lumi.remote.b286acn02")
				configureSpecifics()

			}

		}

	}

}


void processMap(Map map) {

	if (map.cluster == "0012") {
		// Handle the button presses with a debounce.

		int buttonNumber = debouncePress(map)

		// Now work out if our details are correct.
		deviceDetails(buttonNumber)

	} else if (map.cluster == "0000") {

		if (map.attrId == "FFF0") {

			// Curious. This one is received about 3 seconds AFTER the "held" message when a button is still being pressed.
			// The value appears to contain a incrementing counter of some kind; I see values like this:
			//    09AA100541874B011001
			//    09AA100541874D011001
			//    09AA100541874F011001
			//    09AA1005418751011001
			//    09AA1005418753011001
			// Answers on a postcard, please! ;)

			// In the interim, we can use this as an "automated release" seeing as these devices don't support real "released" events.
			// It's a little odd that you can therefore have a held button that's never released, but hey, this is a bonus feature.

			// Essentially, this gives us a press, a hold and then a "long hold" which we report as "released".

			int heldButton = device.currentState("held").value.toInteger()
			sendEvent(name: "released", value: heldButton, isStateChange: true)
			logging("${device} : Trigger : Button ${heldButton} Autoreleased", "info")

		} else {

			// processBasic(map)
			filterThis(map)

		}

	} else {

		filterThis(map)

	}

}


@Field static Boolean isParsing = false
int debouncePress(Map map) {

	if (isParsing) return
	isParsing = true

	int buttonNumber = map.endpoint[-1..-1].toInteger()

	if (map.value == "0100") {

		logging("${device} : Trigger : Button ${buttonNumber} Pressed", "info")
		sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

	} else if (map.value == "0200") {

		logging("${device} : Trigger : Button ${buttonNumber} Double Tapped", "info")
		sendEvent(name: "doubleTapped", value: buttonNumber, isStateChange: true)

	} else if (map.value == "0000") {

		logging("${device} : Trigger : Button ${buttonNumber} Held", "info")
		sendEvent(name: "held", value: buttonNumber, isStateChange: true)

	}

	pauseExecution 200
	isParsing = false

	return buttonNumber

}
