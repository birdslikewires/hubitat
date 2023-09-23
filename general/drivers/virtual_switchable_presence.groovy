/*
 * 
 *  Virtual Switchable Presence Driver
 *	
 */


@Field String driverVersion = "v1.04 (9th March 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int checkEveryMinutes = 1


metadata {

	definition (name: "Virtual Switchable Presence", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/general/drivers/virtual_switchable_presence.groovy") {

		capability "Configuration"
		capability "PresenceSensor"
		capability "Refresh"
		capability "Switch"
		
		attribute "absent", "integer"
		attribute "absentCounter", "string"
		attribute "absentDate", "integer"
		attribute "absentTime", "string"
		attribute "present", "integer"
		attribute "presentCounter", "string"
		attribute "presentDate", "integer"
		attribute "presentTime", "string"

		if (debugMode) {
			command "testCommand"
		}

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

	// We don't check presence, we're informed about it. But we do refresh our values every minute.
	unschedule("checkPresence")
	int randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", refresh)

	long millisNow = new Date().time
	sendEvent(name: "absent", value: 0, isStateChange: false)
	sendEvent(name: "present", value: millisNow, isStateChange: false)
	sendEvent(name: "switch", value: "on", isStateChange: false)

}


void updateSpecifics() {
	// Called by library updated() method.

	return

}


void refresh() {

	long millisNow = new Date().time
	updateDurations(millisNow)

	logging("${device} : Refreshed", "debug")

}


void off() {

	sendEvent(name: "presence", value: "not present")
	sendEvent(name: "switch", value: "off")
	statusChanged("absent")

}


void on() {

	sendEvent(name: "presence", value: "present")
	sendEvent(name: "switch", value: "on")
	statusChanged("present")

}


void statusChanged(String status) {

	logging("${device} : statusChanged : $status", "debug")

	long millisNow = new Date().time
	String dateNow = new Date(millisNow).format('yyyy-MM-dd\'T\'HH:mm:ss').toString()

	if (status.indexOf('absent') >= 0) {

		sendEvent(name: "absent", value: millisNow)
		sendEvent(name: "absentDate", value: dateNow)
		
	} else {

		sendEvent(name: "present", value: millisNow)
		sendEvent(name: "presentDate", value: dateNow)

	}

	runIn(1,refresh)

}


void updateDurations(long millisNow) {

		String presence = device.currentState("presence").value

		if (presence.indexOf('not') >= 0) {

			long wentAbsent = Long.valueOf(device.currentState("absent").value)
			long durationAbsent = millisNow - wentAbsent

			def newDhmsUptime = []
			newDhmsUptime = millisToDhms(durationAbsent)
			String timeAbsent = "${newDhmsUptime[3]}d ${newDhmsUptime[2]}h ${newDhmsUptime[1]}m"

			sendEvent(name: "absentCounter", value: durationAbsent)
			sendEvent(name: "absentTime", value: timeAbsent)

		} else {

			long wentPresent = Long.valueOf(device.currentState("present").value)
			long durationPresent = millisNow - wentPresent

			def newDhmsUptime = []
			newDhmsUptime = millisToDhms(durationPresent)
			String timePresent = "${newDhmsUptime[3]}d ${newDhmsUptime[2]}h ${newDhmsUptime[1]}m"

			sendEvent(name: "presentCounter", value: durationPresent)
			sendEvent(name: "presentTime", value: timePresent)

		}

}


void parse(String description) {

	logging("${device} : Parse : Not sure how I got called, there's no parsing required here!", "warn")

}
