/*
 * 
 *  Xiaomi Aqara Wireless Mini Switch WXKG11LM / WXKG12LM Driver
 *	
 */


@Field String driverVersion = "v1.12 (17th October 2022)"


#include BirdsLikeWires.library
#include BirdsLikeWires.xiaomi
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "Xiaomi Aqara Wireless Mini Switch WXKG11LM / WXKG12LM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_aqara_wireless_mini_switch_wxkg11lm_wxkg12lm.groovy") {

		capability "AccelerationSensor"
		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "SwitchLevel"
		//capability "TemperatureMeasurement"	// Just because you can doesn't mean you should.
		capability "VoltageMeasurement"

		if (debugMode) {
			command "checkPresence"
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

	updateDataValue("encoding", "Xiaomi")

	String modelCheck = "${getDeviceDataByName('model')}"

	if ("$modelCheck" == "lumi.sensor_switch.aq2") {
		// This is the WXKG11LM original 2015 model.

		device.name = "Xiaomi Aqara Wireless Mini Switch WXKG11LMr1"
		sendEvent(name: "numberOfButtons", value: 5, isStateChange: false)
		device.deleteCurrentState("level")

	} else if ("$modelCheck" == "lumi.remote.b1acn01") {
		// This is the WXKG11LM revised 2018 model featuring hold and release.

		device.name = "Xiaomi Aqara Wireless Mini Switch WXKG11LMr2"
		sendEvent(name: "numberOfButtons", value: 5, isStateChange: false)
		sendEvent(name: "level", value: 0, isStateChange: false)

	} else if ("$modelCheck" == "lumi.sensor_switch.aq3") {
		// This is the WXKG12LM with gyroscope for shake functionality.
		// These sacrifice some multi-presses and I've found them less resiliant in poor signal environments. Just my 2p.

		device.name = "Xiaomi Aqara Wireless Mini Switch WXKG12LM"
		sendEvent(name: "numberOfButtons", value: 6, isStateChange: false)
		sendEvent(name: "level", value: 0, isStateChange: false)
		sendEvent(name: "acceleration", value: "inactive", isStateChange: false)

	} else if ("$modelCheck" == "lumi.sensor_swit") {
		// There's a weird truncation of the model string which only occurs with the '12LM. It looks like a firmware bug.

		updateDataValue("model", "lumi.sensor_switch.aq3")
		configureSpecifics()

	} else {

		logging("${device} : Model '$modelCheck' is not known.", "warn")

		if (modelCheck.indexOf('FF42') >= 0) {
			// We may have a raw hex message in here. Mine looked like this:
			// 166C756D692E73656E736F725F7377697463682E61713201FF421A0121BD0B03281C0421A81305214C02062406000000000A2108C6
			
			// If this is a common thing we should move this to the Xiaomi library, but I've only ever seen it on these mini switches.

			modelCheck = modelCheck.split('FF42')[0]
			String extractedModel = hexToText("$modelCheck")

			if (extractedModel.indexOf('lumi.sensor_switch') >= 0) {

				updateDataValue("model", "$extractedModel")
				logging("${device} : Found and updated model name to '$extractedModel'.", "info")

			} else {

				logging("${device} : Extracted '$extractedModel' but this is an unknown device to this driver. Please report to developer.", "warn")

			}

		}

	}

}


void accelerationInactive() {

	sendEvent(name: "acceleration", value: "inactive", isStateChange: true)

}


void setLevel(BigDecimal level) {

	setLevel(level,1)

}


void setLevel(BigDecimal level, BigDecimal duration) {

	BigDecimal safeLevel = level <= 100 ? level : 100
	safeLevel = safeLevel < 0 ? 0 : safeLevel

	String hexLevel = percentageToHex(safeLevel.intValue())

	BigDecimal safeDuration = duration <= 25 ? (duration*10) : 255
	String hexDuration = Integer.toHexString(safeDuration.intValue())

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Got level request of '${level}' (${safeLevel}%) [${hexLevel}] changing over '${duration}' second${pluralisor} (${safeDuration} deciseconds) [${hexDuration}].", "debug")

	sendEvent(name: "level", value: "${safeLevel}")

}


void processMap(Map map) {

	if (map.cluster == "0006") { 
		// Handle button presses for the WXKG11LM 

		int buttonNumber = map.value[-1..-1].toInteger()
		buttonNumber = buttonNumber == 0 ? 1 : buttonNumber

		if (buttonNumber == 2) {
			logging("${device} : Trigger : Button Double Tapped", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
		}

		logging("${device} : Trigger : Button ${buttonNumber} Pressed", "info")
		sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

	} else if (map.cluster == "0012") { 
		// Handle button presses for the WXKG12LM and holds for WXKG11LMr2

		if (map.value == "0100") {

			logging("${device} : Trigger : Button 1 Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else if (map.value == "0200") {

			logging("${device} : Trigger : Button Double Tapped", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
			logging("${device} : Trigger : Button 2 Pressed", "info")
			sendEvent(name: "pushed", value: 2, isStateChange: true)

		} else if (map.value == "0000" || map.value == "1000") {

			state.changeLevelStart = now()
			logging("${device} : Trigger : Button Held", "info")
			sendEvent(name: "held", value: 1, isStateChange: true)
			sendEvent(name: "pushed", value: 3, isStateChange: true)

		} else if (map.value == "1100" || map.value == "FF00") {

			logging("${device} : Trigger : Button Released", "info")
			sendEvent(name: "released", value: 1, isStateChange: true)
			sendEvent(name: "pushed", value: 4, isStateChange: true)

			// Now work out the level we should report based upon the hold duration.

			long millisHeld = now() - state.changeLevelStart
			if (millisHeld > 6000) {
				millisHeld = 0				// In case we don't receive a 'released' message.
			}

			BigInteger levelChange = 0
			levelChange = millisHeld / 6000 * 140
			// That multiplier above is arbitrary - it was 100, but has been increased to account for the delay in detecting hold mode.

			BigDecimal secondsHeld = millisHeld / 1000
			secondsHeld = secondsHeld.setScale(2, BigDecimal.ROUND_HALF_UP)

			logging("${device} : Level : Setting level to ${levelChange} after holding for ${secondsHeld} seconds.", "info")

			setLevel(levelChange)

		} else if (map.value == "1200") {

			logging("${device} : Trigger : Button Shaken", "info")
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
				logging("${device} : Trigger : Button 5 Pressed", "info")
				sendEvent(name: "pushed", value: 5, isStateChange: true)

			}

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}

}
