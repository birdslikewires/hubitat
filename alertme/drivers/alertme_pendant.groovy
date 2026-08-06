/*
 * 
 *  AlertMe Pendant Driver
 *	
 */


@Field String driverVersion = "v1.20 (6th August 2026)"
@Field boolean debugMode = false

#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 2
@Field int rangeEveryHours = 6
@Field String deviceName = "AlertMe Pendant"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/alertme/drivers/alertme_pendant.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Refresh"
		capability "Switch"
		capability "VoltageMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "healthStatus", "enum", ["offline", "online"]
		attribute "lqi", "number"

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "C216", inClusters: "00F0,00C0", outClusters: "", manufacturer: "AlertMe.com", model: "Care Pendant Device", deviceJoinName: "$deviceName"

	}

}


preferences {
	
	input name: "periodicRanging", type: "bool", title: "Enable diagnostics", defaultValue: false
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F6 {11 00 FC 01} {0xC216}"])	   // version information request

}


void configureSpecifics() {

	device.name = "$deviceName"
	sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)

	pairingRangingSequence()

	scheduleRangingIfEnabled()

}


void updateSpecifics() {
	// Called by library updated() method in BirdsLikeWires.library

	scheduleRangingIfEnabled()

}


void deviceOnlineActions() {

	if (device.currentValue("presence") != "present") sendEvent(name: "presence", value: "present")

}


void deviceOfflineActions() {

	if (device.currentValue("presence") != "not present") sendEvent(name: "presence", value: "not present")

}


void off() {

	// The 'off' command will set the Pendant back to idle.
	alertmeCare(0)

}


void on() {

	// The 'on' command notifies "help coming" with three beeps and continuous green flashing.
	alertmeCare(4)

}


void alertmeCareResponse() {

	alertmeCare(3)		// Notifies the user that the system has received the panic call with two beeps and continuous red flashing.

}


void processMap(Map map) {

	if (map.clusterId == "00C0") {

		// Pendant trigger message.

		if (map.command == "0A") {

			logging("${device} : Trigger : Pendant Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)
			unschedule(alertmeCareResponse)
			runInMillis(2000, "alertmeCareResponse")

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
