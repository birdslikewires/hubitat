/*
 * 
 *  AlertMe Pendant Driver v1.00 (20th September 2022)
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

	definition (name: "AlertMe Pendant", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_pendant.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Refresh"
		capability "SignalStrength"
		capability "Switch"

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
			pauseExecution(2000)
			alertmeCare(3)		// Notifies the user that the system has received the panic call with two beeps and continuous red flashing.

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "00F0") {

		// Device Status Cluster
		alertmeDeviceStatus(map)

	} else if (map.clusterId == "00F3") {

		// Receiving Fob messages!
		logging("${device} : WARNING : You can't use a Fob as a Pendant. Please switch back to the AlertMe Fob driver.", "warn")

	} else if (map.clusterId == "00F6") {

		// 00F6 - Discovery Cluster
		alertmeDiscovery(map)

	} else if (map.clusterId == "8001" || map.clusterId == "8032" || map.clusterId == "8038") {

		alertmeSkip(map.clusterId)

	} else {

		reportToDev(map)

	}

}