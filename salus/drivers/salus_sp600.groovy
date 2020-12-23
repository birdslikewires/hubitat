/*
 * 
 *  Salus SP600 Smart Plug Driver v1.14 (23rd December 2020)
 *	
 */


metadata {

	definition (name: "Salus SP600 Smart Plug", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/salus/drivers/salus_sp600.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "Initialize"
		capability "Outlet"
		capability "PowerMeter"
		capability "PresenceSensor"
		capability "Refresh"
		capability "Switch"

		attribute "powerWithUnit", "string"

		fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0004, 0005, 0006, 0402, 0702, FC01", outClusters: "0019", manufacturer: "Computime", model: "SP600", deviceJoinName: "Salus SP600 Smart Plug"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


def installed() {
	// Runs after first pairing.
	logging("${device} : Paired!", "info")
}


def initialize() {

	// Set states to starting values and schedule a single refresh.
	// Runs on reboot, or can be triggered manually.

	// Reset states...

	state.presenceUpdated = 0

	// ...but don't arbitrarily reset the state of the device's main functions or tamper status.

	sendEvent(name: "power", value: 0, unit: "W", isStateChange: false)
	sendEvent(name: "powerWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "presence", value: "not present")
	sendEvent(name: "switch", value: "unknown")

	// Remove disused state variables from earlier versions.
	state.remove("rssi")

	// Stagger our device init refreshes or we run the risk of DDoS attacking our hub on reboot!
	randomSixty = Math.abs(new Random().nextInt() % 60)
	runIn(randomSixty,refresh)

	// Initialisation complete.
	logging("${device} : Initialised", "info")

}


def configure() {

	// Set preferences and ongoing scheduled tasks.
	// Runs after installed() when a device is paired or rejoined, or can be triggered manually.

	initialize()
	unschedule()

	// Default logging preferences.
	device.updateSetting("infoLogging",[value:"true",type:"bool"])
	device.updateSetting("debugLogging",[value:"false",type:"bool"])
	device.updateSetting("traceLogging",[value:"false",type:"bool"])

	// Schedule our refresh.
	int checkEveryHours = 1						
	randomSixty = Math.abs(new Random().nextInt() % 60)
	randomTwentyFour = Math.abs(new Random().nextInt() % 24)
	schedule("${randomSixty} ${randomSixty} ${randomTwentyFour}/${checkEveryHours} * * ? *", refresh)

	// Schedule the presence check.
	int checkEveryMinutes = 6
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Set the operating and reporting modes.
	sendZigbeeCommands(zigbee.onOffConfig())
	powerMeteringConfig()

	// Configuration complete.
	logging("${device} : Configured", "info")

	// Request a refresh.
	refresh()
	
}


def updated() {

	// Runs whenever preferences are saved.

	loggingStatus()
	runIn(3600,debugLogOff)
	runIn(1800,traceLogOff)
	refresh()

}


void loggingStatus() {

	log.info "${device} : Logging : ${infoLogging == true}"
	log.debug "${device} : Debug Logging : ${debugLogging == true}"
	log.trace "${device} : Trace Logging : ${traceLogging == true}"

}


void traceLogOff(){
	
	device.updateSetting("traceLogging",[value:"false",type:"bool"])
	log.trace "${device} : Trace Logging : Automatically Disabled"

}


void debugLogOff(){
	
	device.updateSetting("debugLogging",[value:"false",type:"bool"])
	log.debug "${device} : Debug Logging : Automatically Disabled"

}


void reportToDev(map) {

	String[] receivedData = map.data

	def receivedDataCount = ""
	if (receivedData != null) {
		receivedDataCount = "${receivedData.length} bits of "
	}

	logging("${device} : UNKNOWN DATA! Please report these messages to the developer.", "warn")
	logging("${device} : Received : cluster: ${map.cluster}, clusterId: ${map.clusterId}, attrId: ${map.attrId}, command: ${map.command} with value: ${map.value} and ${receivedDataCount}data: ${receivedData}", "warn")
	logging("${device} : Splurge! : ${map}", "trace")

}


def powerMeteringConfig() {

	// Some other drivers have this configurable, but these work very well from my experience and matches the AlertMe outlets I'm used to.
	// Feel free to tinker with the report times and reportable change if other values better fit your requirements.

	minReportTime=10
	maxReportTime=20
	reportableChange=0x01

	sendZigbeeCommands(zigbee.configureReporting(0x0702, 0x0400, DataType.INT24, minReportTime, maxReportTime, reportableChange))

}


def off() {

	sendZigbeeCommands(zigbee.off())

}


def on() {

	sendZigbeeCommands(zigbee.on())

}


def refresh() {
	
	logging("${device} : Refreshing", "info")
	sendZigbeeCommands(zigbee.readAttribute(0x0702, 0x0400))
	sendZigbeeCommands(zigbee.onOffRefresh())

}


def updatePresence() {

	long millisNow = new Date().time
	state.presenceUpdated = millisNow

}


def checkPresence() {

	// Check how long ago the presence state was updated.

	// It would be suspicious if nothing was received after 4 minutes, but this check runs every 6 minutes so we don't exaggerate a wayward transmission or two.

	long millisNow = new Date().time

	presenceTimeoutMinutes = 4

	if (state.presenceUpdated > 0) {

		long millisElapsed = millisNow - state.presenceUpdated
		long presenceTimeoutMillis = presenceTimeoutMinutes * 60000
		BigDecimal secondsElapsed = millisElapsed / 1000

		if (millisElapsed > presenceTimeoutMillis) {

			sendEvent(name: "presence", value: "not present")
			logging("${device} : Not Present : Last presence report ${secondsElapsed} seconds ago.", "warn")

		} else {

			sendEvent(name: "presence", value: "present")
			logging("${device} : Present : Last presence report ${secondsElapsed} seconds ago.", "debug")

		}

		logging("${device} : checkPresence() : ${millisNow} - ${state.presenceUpdated} = ${millisElapsed} (Threshold: ${presenceTimeoutMillis})", "trace")

	} else {

		logging("${device} : Waiting for first presence report.", "warn")

	}

}


def parse(String description) {

	// Primary parse routine.

	logging("${device} : Parse : $description", "debug")

	sendEvent(name: "presence", value: "present")
	updatePresence()

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Splurge! : ${description}", "warn")

	}

}


void processMap(map) {

	logging("${device} : processMap() : ${map}", "trace")

	if (map.cluster == "0006" || map.clusterId == "0006") {

		// Relay configuration and response handling.

		if (map.command == "01" || map.command == "0A") {

			// Relay States

			// 01 - Prompted Refresh
			// 0A - Automated Refresh

			if (map.value == "01") {

				sendEvent(name: "switch", value: "on")
				logging("${device} : Switch : On", "info")

			} else {

				sendEvent(name: "switch", value: "off")
				logging("${device} : Switch : Off", "info")

			}

		} else if (map.command == "07") {

			// Relay Configuration

			logging("${device} : Relay Configuration : Successful", "info")


		} else if (map.command == "0B") {

			// Relay State Confirmations?

			String[] receivedData = map.data
			def String powerStateHex = receivedData[0]

			if (powerStateHex == "01") {

				sendEvent(name: "switch", value: "on")

			} else {

				sendEvent(name: "switch", value: "off")

			}

		} else if (map.command == "00") {

			logging("${device} : skipping state counter message : ${map}", "trace")

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "0702" || map.clusterId == "0702") {

		// Power configuration and response handling.

		// We also use this to update our presence detection given its frequency.

		if (map.command == "07") {

			// Power Configuration

			logging("${device} : Power Reporting Configuration : Successful", "info")

		} else if (map.command == "01" || map.command == "0A") {

			// Power Usage

			// 01 - Prompted Refresh
			// 0A - Automated Refresh

			def int powerValue = zigbee.convertHexToInt(map.value)
			sendEvent(name: "power", value: powerValue, unit: "W", isStateChange: false)
			sendEvent(name: "powerWithUnit", value: "${powerValue} W", isStateChange: false)
			logging("${device} : power hex value : ${map.value}", "trace")
			logging("${device} : power sensor reports : ${powerValue}", "debug")

			if (map.command == "01") {
				// If this has been requested by the user, return the value in the log.
				logging("${device} : Power : ${powerValue} W", "info")
			}

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "8001" || map.clusterId == "8001") {

		logging("${device} : skipping network address response message : ${map}", "trace")

	} else if (map.cluster == "8021" || map.clusterId == "8021") {

		logging("${device} : skipping discovery message : ${map}", "trace")

	} else if (map.cluster == "8032" || map.clusterId == "8032") {

		logging("${device} : skipping management routing response message : ${map}", "trace")

	} else if (map.cluster == "8038" || map.clusterId == "8038") {

		logging("${device} : skipping management network update notify message : ${map}", "trace")

	} else {

		reportToDev(map)

	}
	
}


void sendZigbeeCommands(List<String> cmds) {

	// All hub commands go through here for immediate transmission and to avoid some method() weirdness.

    logging("${device} : sendZigbeeCommands received : ${cmds}", "trace")
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))

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
