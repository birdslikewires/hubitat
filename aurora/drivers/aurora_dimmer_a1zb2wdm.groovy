/*
 * 
 *  Aurora Dimmer AU-A1ZB2WDM Driver v1.03 (4th January 2022)
 *	
 */


import groovy.transform.Field

@Field boolean debugMode = false

@Field int reportIntervalMinutes = 10

metadata {

	definition (name: "Aurora Dimmer AU-A1ZB2WDM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/aurora/drivers/aurora_dimmer_a1zb2wdm.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "Initialize"
		capability "Light"
		capability "PresenceSensor"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"

		attribute "indicator", "string"

		command "indicatorOn"
		command "indicatorOff"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "8E63", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0019", manufacturer: "Aurora", model: "WallDimmerMaster", deviceJoinName: "Aurora Dimmer AU-A1ZB2WDM"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: true
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: true
	
}


def testCommand() {

	logging("${device} : Test Command", "info")

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
	device.updateSetting("debugLogging",[value:"true",type:"bool"])
	device.updateSetting("traceLogging",[value:"true",type:"bool"])

	// Schedule our refresh.
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${reportIntervalMinutes} * * * ? *", refresh)

	// Schedule the presence check.
	int checkEveryMinutes = 10																// Check presence timestamp every 10 minutes.						
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)				// At X seconds past the minute, every checkEveryMinutes minutes.

	// Configuration complete.
	logging("${device} : Configured", "info")

	updated()
	initialize()
	
}


def updated() {
	// Runs whenever preferences are saved.

	if (!debugMode) {
		//runIn(3600,infoLogOff)	// These devices are so quiet I think we can live without this.
		runIn(2400,debugLogOff)
		runIn(1200,traceLogOff)
	}

	loggingStatus()

}


def initialize() {

	state.clear()
	state.presenceUpdated = 0

	sendEvent(name: "indicator", value: "off", isStateChange: false)
	sendEvent(name: "presence", value: "present", isStateChange: false)

	updated()

	// Initialisation complete.
	logging("${device} : Initialised", "info")

	refresh()

}


def refresh() {
	
	logging("${device} : Refreshing", "debug")
	sendZigbeeCommands([
		"he rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x0000 {}",
		"he rattr 0x${device.deviceNetworkId} 0x01 0x0008 0x0000 {}",
		"he rattr 0x${device.deviceNetworkId} 0x03 0x0006 0x0000 {}"
	])
	//sendZigbeeCommands(zigbee.onOffRefresh())  // Doesn't include the level or indicator status.

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


def off() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"])
	//sendZigbeeCommands(zigbee.off())  // same thing

}


def on() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}"])
	//sendZigbeeCommands(zigbee.on())  // same thing

}


def indicatorOff() {

	// Turns blue backlight LED off. Does not persist with power cycling.
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x03 0x0006 0x00 {}"])

}


def indicatorOn() {

	// Turns blue backlight LED on.
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x03 0x0006 0x01 {}"])

}


def setLevel(BigDecimal level) {
	setLevel(level,1)
}


def setLevel(BigDecimal level, BigDecimal duration) {

	BigDecimal safeLevel = level <= 98 ? level : 100
	String hexLevel = percentageToHex(safeLevel.intValue())

	BigDecimal safeDuration = duration <= 25 ? (duration*10) : 255
	String hexDuration = Integer.toHexString(safeDuration.intValue())

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Got level request of '${level}' (${safeLevel}%) [${hexLevel}] over '${duration}' (${safeDuration} decisecond${pluralisor}) [${hexDuration}].", "debug")

	// The command data is made up of three hex values, the first byte is the level, second is duration, third always seems to be '00'.
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0008 0x04 {${hexLevel} ${hexDuration} 00}"])

}


def checkLevel() {

	unschedule(checkLevel)
	logging("${device} : Checking Level", "debug")
	sendZigbeeCommands([
		"he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0x0000 {}"
	])

}


def updatePresence() {

	long millisNow = new Date().time
	state.presenceUpdated = millisNow
	sendEvent(name: "presence", value: "present")

}


def checkPresence() {

	// Check how long ago the presence state was updated.

	int uptimeAllowanceMinutes = 20			// The hub takes a while to settle after a reboot.

	if (state.presenceUpdated > 0) {

		long millisNow = new Date().time
		long millisElapsed = millisNow - state.presenceUpdated
		long presenceTimeoutMillis = ((reportIntervalMinutes * 2) + 20) * 60000
		long reportIntervalMillis = reportIntervalMinutes * 60000
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

		logging("${device} : checkPresence() : ${millisNow} - ${state.presenceUpdated} = ${millisElapsed}", "trace")
		logging("${device} : checkPresence() : Report interval is ${reportIntervalMillis} ms, timeout is ${presenceTimeoutMillis} ms.", "trace")

	} else {

		logging("${device} : Presence : Waiting for first presence report.", "warn")

	}

}


def parse(String description) {

	// Primary parse routine.

	logging("${device} : Parse : $description", "debug")

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

		// On or off.

		String dimmerOrIndicator = (map.endpoint == "03" || map.sourceEndpoint == "03") ? "Indicator" : "Dimmer"

		String deviceData = (map.data != null) ? map.data[0] : ""

		if (map.value == "01" || deviceData == "01") {

			// On

			if (dimmerOrIndicator == "Dimmer") {

				sendEvent(name: "switch", value: "on")
				runIn(12, checkLevel)

			} else {

				sendEvent(name: "indicator", value: "on")

			}

			logging("${device} : ${dimmerOrIndicator} On", "info")

		} else if (map.value == "00" || deviceData == "00") {

			// Off

			if (dimmerOrIndicator == "Dimmer") {

				sendEvent(name: "switch", value: "off")
				runIn(12, checkLevel)

			} else {

				sendEvent(name: "indicator", value: "off")

			}

			logging("${device} : ${dimmerOrIndicator} Off", "info")

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "0008" || map.clusterId == "0008") {

		// Level

		if (map.command == "01" || map.command == "0A") {

			// Reading

			int currentLevel = hexToPercentage("${map.value}")
			sendEvent(name: "level", value: "${currentLevel}")
			logging("${device} : Level : ${currentLevel}", "debug")

		} else if (map.command == "0B") {

			// Status

			logging("${device} : Fade Beginning", "debug")

		} else {

			reportToDev(map)

		}

	} else {

		reportToDev(map)

	}
	
}


//// Library


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


private BigDecimal hexToBigDecimal(String hex) {

    int d = Integer.parseInt(hex, 16) << 21 >> 21
    return BigDecimal.valueOf(d)

}


private String percentageToHex(Integer pc) {

	BigDecimal safePc = pc > 0 ? (pc*2.54) : 0
	safePc = safePc > 254 ? 254 : safePc
	return Integer.toHexString(safePc.intValue())

}


private Integer hexToPercentage(String hex) {

	String safeHex = hex.take(2)
    Integer pc = Integer.parseInt(safeHex, 16) << 21 >> 21
	return pc / 2.54

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
