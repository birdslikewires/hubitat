/*
 * 
 *  IKEA Symfonisk Sound Controller E1744 Driver v1.05 (24th October 2021)
 *	
 */

import groovy.transform.Field

metadata {

	definition (name: "IKEA Symfonisk Sound Controller E1744", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/ikea/drivers/ikea_symfonisk_sound_controller_e1744.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "Initialize"
		capability "Momentary"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Refresh"
		capability "ReleasableButton"
		capability "Switch"
		capability "SwitchLevel"

		attribute "batteryState", "string"
		attribute "batteryWithUnit", "string"

		attribute "direction", "string"
		attribute "levelChange", "integer"

		//command "checkPresence"

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,1000", outClusters: "0003,0004,0006,0008,0019,1000", manufacturer: "IKEA of Sweden", model: "SYMFONISK Sound Controller", deviceJoinName: "Symfonisk Sound Controller", application: "21"

	}

}

@Field int reportIntervalSeconds = 3600		// How often should the device report in.
@Field int presenceTimeoutMinutes = 140		// Allow one missed report with some leeway.

preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: true
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: true
	
}


def installed() {
	// Runs after first pairing.
	logging("${device} : Paired!", "info")
}


def initialize() {

	// Set states to starting values and schedule a single refresh.
	// Runs on reboot, or can be triggered manually.

	// Reset states.
	state.clear()
	state.presenceUpdated = 0
	sendEvent(name: "presence", value: "present", isStateChange: false)

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
	device.updateSetting("debugLogging",[value:"true",type:"bool"])
	device.updateSetting("traceLogging",[value:"true",type:"bool"])

	// Important Bit
	sendZigbeeCommands(zigbee.onOffConfig())
	sendZigbeeCommands(zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, DataType.UINT8, reportIntervalSeconds, reportIntervalSeconds, 0x00))   // Report in regardless of other changes.
	sendZigbeeCommands(zigbee.enrollResponse())

	// Schedule the presence check.
	int checkEveryMinutes = 10																					// Check presence timestamp every 10 minutes.						
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)									// At X seconds past the minute, every checkEveryMinutes minutes.

	// Configuration complete.
	logging("${device} : Configured", "info")

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


void off() {

	sendEvent(name: "switch", value: "off")
	sendEvent(name: "pushed", value: 1, isStateChange: true)
	logging("${device} : Switch : Off", "info")

}


void on() {

	sendEvent(name: "switch", value: "on")
	sendEvent(name: "pushed", value: 1, isStateChange: true)
	logging("${device} : Switch : On", "info")

}


void push(buttonId) {
	
	sendEvent(name:"pushed", value: buttonId, isStateChange:true)
	
}

void doubleTap(buttonId) {
	
	sendEvent(name:"doubleTapped", value: buttonId, isStateChange:true)
	
}

void hold(buttonId) {
	
	sendEvent(name:"held", value: buttonId, isStateChange:true)
	
}


void release(buttonId) {
	
	sendEvent(name:"released", value: buttonId, isStateChange:true)
	
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
	logging("${device} : setLevel : Got level request of '${level}' (${safeLevel}%) [${hexLevel}] over '${duration}' (${safeDuration} decisecond${pluralisor}) [${hexDuration}].", "debug")

	sendEvent(name: "level", value: "${safeLevel}")

}


void refresh() {

	// Battery status can be requested if the command is sent within about 3 seconds of an actuation.
	// I considered removing this, but it can be useful for forcing a battery read when the device is to hand.

	logging("${device} : Refreshing", "info")
	sendZigbeeCommands(zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021))

}


def updatePresence() {

	long millisNow = new Date().time
	state.presenceUpdated = millisNow

}


def checkPresence() {

	// Check how long ago the presence state was updated.

	uptimeAllowanceMinutes = 20			// The hub takes a while to settle after a reboot.

	if (state.presenceUpdated > 0) {

		long millisNow = new Date().time
		long millisElapsed = millisNow - state.presenceUpdated
		long presenceTimeoutMillis = presenceTimeoutMinutes * 60000
		BigInteger secondsElapsed = BigDecimal.valueOf(millisElapsed / 1000)
		BigInteger hubUptime = location.hub.uptime

		if (millisElapsed > presenceTimeoutMillis) {

			if (hubUptime > uptimeAllowanceMinutes * 60) {

				sendEvent(name: "presence", value: "not present")
				logging("${device} : Presence : Not Present! Last report received ${secondsElapsed} seconds ago.", "warn")

			} else {

				logging("${device} : Presence : Ignoring overdue presence reports for ${uptimeAllowanceMinutes} minutes. The hub was rebooted ${hubUptime} seconds ago.", "debug")

			}

		} else {

			sendEvent(name: "presence", value: "present")
			logging("${device} : Presence : Last presence report ${secondsElapsed} seconds ago.", "debug")

		}

		logging("${device} : checkPresence() : ${millisNow} - ${state.presenceUpdated} = ${millisElapsed} (Threshold: ${presenceTimeoutMillis} ms)", "trace")

	} else {

		logging("${device} : Presence : Waiting for first presence report.", "warn")

	}

}


def parse(String description) {

	// Primary parse routine.

	logging("${device} : parse() : $description", "trace")

	updatePresence()

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Splurge! : ${description}", "warn")

	}

}


def processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedData = map.data

	logging("${device} : Processing : cluster: ${map.cluster}, clusterId: ${map.clusterId}, attrId: ${map.attrId}, command: ${map.command} with value: ${map.value} and ${receivedDataCount}data: ${receivedData}", "debug")

	if (map.cluster == "0001") { 

		if (map.attrId == "0021") {

			// Okay, battery reporting on this thing is weird. It's not a voltage, but a percentage.
			// According to the Zigbee Cluster User Guide, the decimal value from the hex should be multiplied by 0.5 to give percent.
			// But the readings are all over the shop. Might be worth pairing to the Tradfri hub and checking for firmware updates?

			// Hey, in the meantime, let's just fudge it!
			state.batteryOkay = true

			// Report the battery... percentage.
			def batteryHex = "undefined"
			BigDecimal batteryPercentage = 0

			batteryHex = map.value
			logging("${device} : batteryHex : ${batteryHex}", "trace")

			batteryPercentage = zigbee.convertHexToInt(batteryHex)
			logging("${device} : batteryPercentage sensor value : ${batteryPercentage}", "debug")

			//batteryPercentage = batteryPercentage * 0.5
			batteryPercentage = batteryPercentage.setScale(0, BigDecimal.ROUND_HALF_UP)
			batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage
			batteryPercentage = batteryPercentage < 0 ? 0 : batteryPercentage

			//Integer batteryDifference = Math.abs(device.currentState("battery").value.toInteger() - batteryPercentage)

			logging("${device} : batteryPercentage : ${batteryPercentage}", "debug")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%")
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
			sendEvent(name: "batteryState", value: "discharging")

		} else {

			logging("${device} : Skipped : Power Cluster with no data.", "debug")

		}

	} else if (map.clusterId == "0001") { 

		logging("${device} : Skipped : Power Cluster with no data.", "debug")

	} else if (map.clusterId == "0006") { 

		if (receivedData.length == 0) {

			parsePress(map)

		} else {

			logging("${device} : Skipped : On/Off Cluster with extraneous data.", "debug")

		}

	} else if (map.clusterId == "0008") {

		parsePress(map)

	} else if (map.clusterId == "0013") {

		logging("${device} : Skipped : Device Announce Broadcast", "debug")

	} else if (map.clusterId == "0500") {

		logging("${device} : Skipped : IAS Zone", "debug")

	} else if (map.clusterId == "8021") {

		logging("${device} : Skipped : Bind Response", "debug")

	} else if (map.clusterId == "8022") {

		logging("${device} : Skipped : Unbind Response", "debug")

	} else {

		// Not a clue what we've received.
		reportToDev(map)

	}

	return null

}


@Field static Boolean isParsing = false
def parsePress(Map map) {

	if (isParsing) return
	isParsing = true

	// We'll figure out the button numbers in a tick.
	int buttonNumber = 0

	if (map.clusterId == "0006") { 

		// This is a press of the button.
		buttonNumber = 1

		logging("${device} : Trigger : Button ${buttonNumber} Pressed", "info")
		sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
		
		device.currentState("switch").value == "off" ? on() : off()

	} else if (map.clusterId == "0008") {

		if (map.command == "01") {

			// This is a turn of the dial starting.
			buttonNumber = 2

			String[] receivedData = map.data

			if (receivedData[0] == "00") {

				buttonNumber = 2
				state.changeLevelStart = now()
				logging("${device} : Trigger : Dial (Button ${buttonNumber}) Turning Clockwise", "info")
				sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
				sendEvent(name: "direction", value: "clockwise")
				sendEvent(name: "held", value: buttonNumber, isStateChange: true)
				sendEvent(name: "switch", value: "on", isStateChange: false)

			} else if (receivedData[0] == "01") {

				buttonNumber = 3
				state.changeLevelStart = now()
				logging("${device} : Trigger : Dial (Button ${buttonNumber}) Turning Anticlockwise", "info")
				sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
				sendEvent(name: "direction", value: "anticlockwise")
				sendEvent(name: "held", value: buttonNumber, isStateChange: true)
				sendEvent(name: "switch", value: "on", isStateChange: false)

			} else {

				reportToDev(map)

			}

		} else if (map.command == "02") {

			// This is a multi-press of the button.
			buttonNumber = 1

			String[] receivedData = map.data

			if (receivedData[0] == "00") {

				// Double-press is a supported Hubitat action, so just report the event.
				logging("${device} : Trigger : Button ${buttonNumber} Double Pressed", "info")
				sendEvent(name: "doubleTapped", value: buttonNumber, isStateChange: true)
				sendEvent(name: "held", value: buttonNumber, isStateChange: true)

			} else if (receivedData[0] == "01") {

				// Triple-pressing is not a supported Hubitat action, but this device doesn't support hold or release on the button, so we can use that for triggers.
				logging("${device} : Trigger : Button ${buttonNumber} Triple Pressed", "info")
				sendEvent(name: "released", value: buttonNumber, isStateChange: true)

			} else {

				reportToDev(map)

			}

		} else if (map.command == "03") {

			// This is a turn of the dial stopping.
			// There's no differentiation in the data sent, so we work out from which direction we're stopping using the previous hold state.

			buttonNumber = device.currentState("held").value.toInteger()

			logging("${device} : Trigger : Dial (Button ${buttonNumber}) Stopped", "info")
			sendEvent(name: "released", value: buttonNumber, isStateChange: true)

			// Now work out the level we should change to based upon the time spent changing.

			Integer initialLevel = device.currentState("level").value.toInteger()

			long millisTurning = now() - state.changeLevelStart
			if (millisTurning > 6000) {
				millisTurning = 0				// In case the messages don't stop we could end up at full brightness or VOLUME!
			}

			BigInteger levelChange = 0
			levelChange = millisTurning / 6000 * 100

			BigDecimal targetLevel = 0

			if (buttonNumber == 2) {

				targetLevel = device.currentState("level").value.toInteger() + levelChange

			} else {

				targetLevel = device.currentState("level").value.toInteger() - levelChange
				levelChange *= -1

			}

			logging("${device} : Level : Dial (Button ${buttonNumber}) - Changing from initialLevel '${initialLevel}' by levelChange '${levelChange}' after millisTurning for ${millisTurning} ms.", "debug")

			sendEvent(name: "levelChange", value: levelChange, isStateChange: true)

			setLevel(targetLevel)

		} else {

			reportToDev(map)

		}

	}

	pauseExecution 110
	isParsing = false

}


void sendZigbeeCommands(List<String> cmds) {

	// All hub commands go through here for immediate transmission and to avoid some method() weirdness.

    logging("${device} : sendZigbeeCommands received : ${cmds}", "trace")
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))

}


private String[] millisToDhms(int millisToParse) {

	long secondsToParse = millisToParse / 1000

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


private String percentageToHex(Integer pc) {

	BigDecimal safePc = pc > 0 ? (pc*2.55) : 0
	safePc = safePc > 255 ? 255 : safePc
	return Integer.toHexString(safePc.intValue())

}


private Integer hexToPercentage(String hex) {

	String safeHex = hex.take(2)
    Integer pc = Integer.parseInt(safeHex, 16) << 21 >> 21
	return pc / 2.55

}


private BigDecimal hexToBigDecimal(String hex) {

    int d = Integer.parseInt(hex, 16) << 21 >> 21
    return BigDecimal.valueOf(d)

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
