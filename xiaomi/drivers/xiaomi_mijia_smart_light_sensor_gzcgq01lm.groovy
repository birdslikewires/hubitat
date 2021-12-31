/*
 * 
 *  Xiaomi Mijia Smart Light Sensor GZCGQ01LM Driver v1.00 (31st December 2021)
 *	
 */


import groovy.transform.Field

@Field boolean debugMode = true

@Field int reportIntervalMinutes = 60		// How often the device should be reporting in.

metadata {

	definition (name: "Xiaomi Mijia Smart Light Sensor GZCGQ01LM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_mijia_smart_light_sensor_gzcgq01lm.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "IlluminanceMeasurement"
		capability "Initialize"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Sensor"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "number"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "illuminanceDirection", "string"
		attribute "illuminanceWithUnit", "string"

		if (debugMode) {
			command "checkPresence"
			command "parseThis"
		}

		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "LUMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "GZCGQ01LM", application: "1A"
		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "XIAOMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "GZCGQ01LM", application: "1A"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: true
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: true
	
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

	// Schedule the presence check.
	int checkEveryMinutes = 10																// Check presence timestamp every 10 minutes.						
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)				// At X seconds past the minute, every checkEveryMinutes minutes.

	// Configuration complete.
	logging("${device} : Configured", "info")

	initialize()

}


def initialize() {

	state.clear()
	state.presenceUpdated = 0

	sendEvent(name: "presence", value: "present", isStateChange: false)

	updated()

	// Initialisation complete.
	logging("${device} : Initialised", "info")

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


void push(buttonId) {
	
	sendEvent(name:"pushed", value: buttonId, isStateChange:true)
	
}


def updatePresence() {

	long millisNow = new Date().time
	state.presenceUpdated = millisNow

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

	updatePresence()

	logging("${device} : parse() : $description", "trace")

	Map descriptionMap = null
	String parseType = "Zigbee"

	if (description.indexOf('catchall:') >= 0 || description.indexOf('encoding: 10') >= 0 || description.indexOf('encoding: 20') >= 0 || description.indexOf('encoding: 21') >= 0) {

		// Normal encoding should bear some resemblance to the Zigbee Cluster Library Specification
		logging("${device} : Parse : Processing against Zigbee cluster specification.", "debug")
		descriptionMap = zigbee.parseDescriptionAsMap(description)

	} else {

		// Anything else is likely specific to Xiaomi, so we'll just slice and dice the string we receive.
		logging("${device} : Parse : Processing what we're assuming is Xiaomi structured data.", "debug")
		descriptionMap = description.split(', ').collectEntries {
			entry -> def pair = entry.split(': ')
			[(pair.first()): pair.last()]
		}
		parseType = "Xiaomi"

	}

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse ${parseType} specification data. Please report these messages to the developer.", "warn")
		logging("${device} : Parse Failed Here : ${description}", "warn")

	}

}


def processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedValue = map.value

	if (map.cluster == "0001") { 

		if (map.attrId == "0020") {
			
			// Report the battery voltage and calculated percentage.
			def batteryVoltageHex = "undefined"
			BigDecimal batteryVoltage = 0

			batteryVoltageHex = map.value
			logging("${device} : batteryVoltageHex : ${batteryVoltageHex}", "trace")

			batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex)
			logging("${device} : batteryVoltage sensor value : ${batteryVoltage}", "debug")

			batteryVoltage = batteryVoltage.setScale(2, BigDecimal.ROUND_HALF_UP) / 10

			logging("${device} : batteryVoltage : ${batteryVoltage}", "debug")
			sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V")
			sendEvent(name: "batteryVoltageWithUnit", value: "${batteryVoltage} V")

			BigDecimal batteryPercentage = 0
			BigDecimal batteryVoltageScaleMin = 2.1
			BigDecimal batteryVoltageScaleMax = 3.0

			if (batteryVoltage >= batteryVoltageScaleMin) {

				state.batteryOkay = true

				batteryPercentage = ((batteryVoltage - batteryVoltageScaleMin) / (batteryVoltageScaleMax - batteryVoltageScaleMin)) * 100.0
				batteryPercentage = batteryPercentage.setScale(0, BigDecimal.ROUND_HALF_UP)
				batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage

				def batteryLogLevel = batteryPercentage > 20 ? "info" : "warn"
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", $batteryLogLevel)

				sendEvent(name: "battery", value:batteryPercentage, unit: "%")
				sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
				sendEvent(name: "batteryState", value: "discharging")

			} else {

				// Very low voltages indicate an exhausted battery which requires replacement.

				state.batteryOkay = false

				batteryPercentage = 0

				logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
				sendEvent(name: "battery", value:batteryPercentage, unit: "%")
				sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
				sendEvent(name: "batteryState", value: "exhausted")

			}

		} else {

			// Not a clue what we've received.
			reportToDev(map)

		}

	} else if (map.clusterId == "0003") {

		if (map.command == "01") {

			// Scrounge more value! We can capture a short press of the reset button and make it useful.
			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else {

			// Not a clue what we've received.
			reportToDev(map)

		} 

	} else if (map.cluster == "0400") {

		Integer lux = Integer.parseInt(map.value,16)
		Integer luxTolerance = 200
		Integer luxVariance = Math.abs(state.rawLux - lux)

		if (state.rawLux == null || luxVariance > luxTolerance) {

			state.rawLux = lux
			lux = lux > 0 ? Math.round(Math.pow(10,(lux/10000)) - 1) : 0

			def lastLux = device.currentState("illuminance").value.toInteger()
			String illuminanceDirection = lux > lastLux ? "brightening" : "darkening"
			String illuminanceDirectionLog = illuminanceDirection.capitalize()

			logging("${device} : Lux : ${illuminanceDirectionLog} from ${lastLux} to ${lux} lux.", "info")
			sendEvent(name: "illuminance", value: lux, unit: "lux")
			sendEvent(name: "illuminanceDirection", value: "${illuminanceDirection}")
			sendEvent(name: "illuminanceWithUnit", value: "${lux} lux")

		} else {

			logging("${device} : Lux : Variance of ${luxVariance} (previously ${state.rawLux}, now ${lux}) is within tolerance.", "debug")

		}

	} else {

		// Not a clue what we've received.
		reportToDev(map)

	}

	return null

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
