/*
 * 
 *  AlertMe Button Driver
 *	
 */


@Field String driverVersion = "v1.28 (1st March 2023)"


#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 2		// The real reporting interval of the device.
@Field int checkEveryMinutes = 6			// How often we should check for presence.
@Field int rangeEveryHours = 6				// How often we run a ranging report.


metadata {

	definition (name: "AlertMe Button", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_button.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Refresh"
		capability "ReleasableButton"
		capability "SignalStrength"
		capability "TamperAlert"
		capability "TemperatureMeasurement"
		capability "VoltageMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F2,00F1", outClusters: "", manufacturer: "AlertMe.com", model: "Button Depice", deviceJoinName: "AlertMe Button"
		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F2,00F1", outClusters: "", manufacturer: "AlertMe.com", model: "Button Device", deviceJoinName: "AlertMe Button"
		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F1,00F2", outClusters: "", manufacturer: "AlertMe.com", model: "Button Device", deviceJoinName: "AlertMe Button"

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

	device.name = "AlertMe Button"
	sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)

	state.operatingMode = "normal"

	// Schedule ranging report.
	randomSixty = Math.abs(new Random().nextInt() % 60)
	randomTwentyFour = Math.abs(new Random().nextInt() % 24)
	schedule("${randomSixty} ${randomSixty} ${randomTwentyFour}/${rangeEveryHours} * * ? *", rangingMode)

}


void updateSpecifics() {
	// Called by library updated() method in BirdsLikeWires.library

	rangingMode()

}


void processMap(Map map) {

	if (map.clusterId == "00F3") {

		// Button trigger message.

		// On the Button a push is always sent on a press, but the release is sent only when the button is held for a moment.

		if (map.command == "00") {

			logging("${device} : Trigger : Button Released", "info")
			sendEvent(name: "released", value: 1, isStateChange: true)

		} else if (map.command == "01") {

			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else {

			reportToDev(map)

		}

	} else {

		reportToDev(map)

	}

}
