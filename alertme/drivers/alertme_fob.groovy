/*
 * 
 *  AlertMe Fob Driver v1.25 (27th June 2022)
 *	
 */


#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 2
@Field int checkEveryMinutes = 1
@Field int rangeEveryHours = 6


metadata {

	definition (name: "AlertMe Fob", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_fob.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Refresh"
		capability "ReleasableButton"
		capability "SignalStrength"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "C216", inClusters: "00F0,00C0", outClusters: "", manufacturer: "AlertMe.com", model: "Care Pendant Device", deviceJoinName: "AlertMe Pendant"
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


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	if (map.clusterId == "0006") {

		// Match Descriptor Request Response
		logging("${device} : Sending Match Descriptor Response", "debug")
		sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x8006 {00 00 00 01 02} {0xC216}"])

	} else if (map.clusterId == "00C0") {

		// Pendant trigger message.

		if (map.command == "0A") {

			logging("${device} : Trigger : Pendant Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00C0 {11 00 FD 01} {0xC216}"])

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "00F0") {

		// 00F0 - Device Status Cluster
		alertmeDeviceStatus(map)

	} else if (map.clusterId == "00F3") {

		// Trigger cluster.

		// On the Button a press is always sent when pushed, but the release is sent only when the button is held for a moment.
		// On the Keyfob both press and release are always sent, regardless of how long the button is held.
		// For some reason, key fobs are very 'bouncy' and often send more than one press or release per actuation, sometimes with a long delay.

		// IMPORTANT! Always force 'isStateChange: true' on sendEvent, otherwise pressing the same button more than once won't trigger anything!

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

	} else if (map.clusterId == "00F6") {

		// 00F6 - Discovery Cluster
		alertmeDiscovery(map)

	} else if (map.clusterId == "8001" || map.clusterId == "8038") {

		alertmeSkip(map.clusterId)

	} else if (map.clusterId == "8032" ) {

		// These clusters are sometimes received when joining new devices to the mesh.
		//   8032 arrives with 80 bytes of data, probably routing and neighbour information.
		// We don't do anything with this, the mesh re-jigs itself and is a known thing with AlertMe devices.
		logging("${device} : New join has triggered a routing table reshuffle.", "debug")

	} else {

		reportToDev(map)

	}

}
