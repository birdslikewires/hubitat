/*
 * 
 *  Virtual Switchable Presence v1.01 (22nd November 2021)
 *	
 */


metadata {

	definition (name: "Virtual Switchable Presence", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/generic/drivers/virtual_switchable_presence.groovy") {

		capability "PresenceSensor"
		capability "Switch"
		
		attribute "statusChanged", "integer"
		attribute "timeStatusChanged", "string"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


def installed() {
	// Runs after first installation.
	logging("${device} : Installed", "info")
	configure()
	initialize()
}


def configure() {

	unschedule()

	// Default logging preferences.
	device.updateSetting("infoLogging",[value:"true",type:"bool"])
	device.updateSetting("debugLogging",[value:"false",type:"bool"])
	device.updateSetting("traceLogging",[value:"false",type:"bool"])
	
	// Configuration complete.
	logging("${device} : Configured", "info")

}


def initialize() {

	sendEvent(name: "presence", value: "present", isStateChange: false)
	sendEvent(name: "switch", value: "on", isStateChange: false)

	updated()

	// Initialisation complete.
	logging("${device} : Initialised", "info")

}


def updated() {

	// Runs whenever preferences are saved.

	loggingStatus()
	runIn(3600,infoLogOff)
	runIn(2400,debugLogOff)
	runIn(1200,traceLogOff)
	refresh()

}


void loggingStatus() {

	log.info "${device} : Logging : ${infoLogging == true}"
	log.debug "${device} : Debug Logging : ${debugLogging == true}"
	log.trace "${device} : Trace Logging : ${traceLogging == true}"

}


void traceLogOff(){
	
	log.trace "${device} : Trace Logging : Automatically Disabled"
	device.updateSetting("traceLogging",[value:"false",type:"bool"])

}

void debugLogOff(){
	
	log.debug "${device} : Debug Logging : Automatically Disabled"
	device.updateSetting("debugLogging",[value:"false",type:"bool"])

}


void infoLogOff(){
	
	log.info "${device} : Info Logging : Automatically Disabled"
	device.updateSetting("infoLogging",[value:"false",type:"bool"])

}


void refresh() {

	logging("${device} : Refreshing", "info")

}

def off() {

	sendEvent(name: "presence", value: "not present")
	sendEvent(name: "switch", value: "off")
	statusChanged()

}


def on() {

	sendEvent(name: "presence", value: "present")
	sendEvent(name: "switch", value: "on")
	statusChanged()

}


def statusChanged() {

	long millisNow = new Date().time
	def dateNow = new Date(millisNow).format('yyyy-MM-dd\'T\'HH:mm:ss').toString()
	sendEvent(name: "statusChanged", value: millisNow)
	sendEvent(name: "timeStatusChanged", value: dateNow)

}


def parse(String description) {

	// Primary parse routine.

	logging("${device} : Parse : $description", "debug")

}


private String[] millisToDhms(BigInteger millisToParse) {

	BigInteger secondsToParse = millisToParse / 1000

	def dhms = []
	dhms.add(secondsToParse % 60)
	secondsToParse = secondsToParse / 60
	dhms.add(secondsToParse % 60)
	secondsToParse = secondsToParse / 60
	dhms.add(secondsToParse % 24)
	secondsToParse = secondsToParse / 24
	dhms.add(secondsToParse % 365)
	return dhms

}


private boolean logging(String message, String level) {

	boolean didLog = false

	if (level == "error") {
		log.error "$message"
		didLog = true
	}

	if (level == "warn") {
		log.warn "$message"
		didLog = true
	}

	if (traceLogging && level == "trace") {
		log.trace "$message"
		didLog = true
	}

	if (debugLogging && level == "debug") {
		log.debug "$message"
		didLog = true
	}

	if (infoLogging && level == "info") {
		log.info "$message"
		didLog = true
	}

	return didLog

}
