/*
 * 
 *  AlertMe Fob Driver
 *	
 */


@Field String driverVersion = "v1.30 (27th February 2023)"


#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 2		// The real reporting interval of the device.
@Field int checkEveryMinutes = 1			// How often we should check for presence.
@Field int rangeEveryHours = 6				// How often we run a ranging report.


metadata {

	definition (name: "AlertMe Fob", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_fob.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Refresh"
		capability "ReleasableButton"
		capability "SignalStrength"
		capability "VoltageMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F4,00F1", outClusters: "", manufacturer: "AlertMe.com", model: "Keyfob Device", deviceJoinName: "AlertMe Fob"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F6 {11 00 FC 01} {0xC216}"])	   // version information request

}


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.alertme

	String modelCheck = "${getDeviceDataByName('model')}"

	if ("${modelCheck}" == "Care Pendant Device") {

		device.name = "AlertMe Pendant"
		sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)

	} else if ("${modelCheck}" == "Keyfob Device") {

		device.name = "AlertMe Fob"
		sendEvent(name: "numberOfButtons", value: 2, isStateChange: false)

		state.lastAwayPress = 0
		state.lastAwayRelease = 0
		state.lastHomePress = 0
		state.lastHomeRelease = 0

	}

}


void updateSpecifics() {
	// Called by library updated() method in BirdsLikeWires.library

	return

}


void processMap(Map map) {

	if (map.clusterId == "00C0") {

		// Pendant trigger message.

		if (map.command == "0A") {

			logging("${device} : Trigger : Pendant Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			alertmeCare(0)	// Plonks the pendant straight back into idle mode.

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "00F3") {

		// Fob trigger message.

		// On the Keyfob both press and release are always sent, regardless of how long the button is held.
		// This can be beneficial in case of a lost transmission. Both press and release could be bound to the same non-toggling action.

		// For some reason, key fobs are very 'bouncy' and often send more than one press or release per actuation, sometimes with a long delay.
		// This could again be for reasons of resiliance, as these were originally designed for arming or disarming the security system.

		long buttonDebounceTimeoutMillis = 8000

		int buttonNumber
		String buttonName
		if (map.data[0] == "00") {
			buttonNumber = 1
			buttonName = "Home"
		} else {
			buttonNumber = 2
			buttonName = "Away"
		}

		long millisNow = new Date().time
		long millisElapsedAwayPress = millisNow - state.lastAwayPress
		long millisElapsedAwayRelease = millisNow - state.lastAwayRelease
		long millisElapsedHomePress = millisNow - state.lastHomePress
		long millisElapsedHomeRelease = millisNow - state.lastHomeRelease

		if (map.command == "00") {
			// Release
			
			if (buttonNumber == 1) {
				// Home Button

				if (millisElapsedHomeRelease > buttonDebounceTimeoutMillis) {

					state.lastHomeRelease = millisNow
					logging("${device} : Trigger : Button ${buttonNumber} (${buttonName}) Released", "info")
					sendEvent(name: "released", value: buttonNumber, isStateChange: true)

				} else {

					logging("${device} : Debounced : Button ${buttonNumber} (${buttonName}) Release", "debug")

				}

			} else {
				// Away Button

				if (millisElapsedAwayRelease > buttonDebounceTimeoutMillis) {

					state.lastAwayRelease = millisNow
					logging("${device} : Trigger : Button ${buttonNumber} (${buttonName}) Released", "info")
					sendEvent(name: "released", value: buttonNumber, isStateChange: true)

				} else {

					logging("${device} : Debounced : Button ${buttonNumber} (${buttonName}) Release", "debug")

				}

			}

		} else if (map.command == "01") {
			// Press

			if (buttonNumber == 1) {
				// Home Button

				if (millisElapsedHomePress > buttonDebounceTimeoutMillis) {

					state.lastHomePress = millisNow
					logging("${device} : Trigger : Button ${buttonNumber} (${buttonName}) Pressed", "info")
					sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

				} else {

					logging("${device} : Debounced : Button ${buttonNumber} (${buttonName}) Press", "debug")

				}

			} else {
				// Away Button

				if (millisElapsedAwayPress > buttonDebounceTimeoutMillis) {

					state.lastAwayPress = millisNow
					logging("${device} : Trigger : Button ${buttonNumber} (${buttonName}) Pressed", "info")
					sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

				} else {

					logging("${device} : Debounced : Button ${buttonNumber} (${buttonName}) Press", "debug")

				}

			}

		} else {

			reportToDev(map)

		}

	} else {

		reportToDev(map)

	}

}
