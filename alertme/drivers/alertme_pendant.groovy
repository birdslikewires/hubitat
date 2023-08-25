/*
 * 
 *  AlertMe Pendant Driver
 *	
 */


@Field String driverVersion = "v1.06 (25th August 2023)"


#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 2
@Field int checkEveryMinutes = 1
@Field int rangeEveryHours = 6


metadata {

	definition (name: "AlertMe Pendant", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_pendant.groovy") {

		capability "Battery"
		capability "Configuration"
		//capability "PresenceSensor"	// to be re-enabled as this is a real thing this device would be used for
		capability "PushableButton"
		capability "Refresh"
		capability "SignalStrength"
		capability "Switch"
		capability "VoltageMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "C216", inClusters: "00F0,00C0", outClusters: "", manufacturer: "AlertMe.com", model: "Care Pendant Device", deviceJoinName: "AlertMe Pendant"

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

	device.name = "AlertMe Pendant"
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


void off() {

	// The 'off' command will set the Pendant back to idle.
	alertmeCare(0)

}


void on() {

	// The 'on' command notifies "help coming" with three beeps and continuous green flashing.
	alertmeCare(4)

}


void processMap(Map map) {

	if (map.clusterId == "0006") {

		// Match Descriptor Request Response
		logging("${device} : Sending Match Descriptor Response", "debug")
		sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x8006 {00 00 00 01 02} {0xC216}"])

	} else if (map.clusterId == "00C0") {

		// Pendant trigger message.

		if (map.command == "0A") {

			logging("${device} : Trigger : Pendant Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			pauseExecution(2000)
			alertmeCare(3)		// Notifies the user that the system has received the panic call with two beeps and continuous red flashing.

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "00F3") {

		// Receiving Fob messages!
		logging("${device} : WARNING : You can't use a Fob as a Pendant. Please switch back to the AlertMe Fob driver.", "warn")

	} else {

		reportToDev(map)

	}

}
