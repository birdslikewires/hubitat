/*
 * 
 *  IKEA Tradfri Signal Repeater E1746 Driver v1.01 (6th January 2022)
 *	
 */


import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 10


metadata {

	definition (name: "IKEA Tradfri Signal Repeater E1746", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/ikea/drivers/ikea_tradfri_signal_repeater_e1746.groovy") {

		capability "Configuration"
		capability "Initialize"
		capability "PresenceSensor"
		capability "Refresh"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,0009,0B05,1000,FC7C", outClusters: "0019,0020,1000", manufacturer: "IKEA of Sweden", model: "TRADFRI signal repeater", deviceJoinName: "Tradfri Signal Repeater", application: "20"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


def testCommand() {

	logging("${device} : Test Command", "info")

}


def installed() {
	// Runs after first installation.

	logging("${device} : Installed", "info")
	configure()

}


def configure() {

	unschedule()

	int randomSixty

	// Default logging preferences.
	device.updateSetting("infoLogging", [value: "true", type: "bool"])
	device.updateSetting("debugLogging", [value: "${debugMode}", type: "bool"])
	device.updateSetting("traceLogging", [value: "${debugMode}", type: "bool"])

	// Schedule our refresh.
	int reportEveryMinutes = (reportIntervalMinutes < 60) ? reportIntervalMinutes : 59
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${reportEveryMinutes} * * * ? *", refresh)

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

	removeDataValue("driver")
	removeDataValue("endpointId")
	removeDataValue("softwareBuild")
	removeDataValue("firmwareMT")

	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Request application value, manufacturer, model name and simple descriptor data.
	sendZigbeeCommands([
		"he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x0001 {}",
		"he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x0004 {}",
		"he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x0005 {}",
		"he raw ${device.deviceNetworkId} 0x0000 0x0000 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} 01} {0x0000}"
	])

	logging("${device} : Initialised", "info")

	updated()

}


def updated() {
	// Runs when preferences are saved.

	unschedule(debugLogOff)
	unschedule(traceLogOff)

	if (!debugMode) {
		runIn(2400,debugLogOff)
		runIn(1200,traceLogOff)
	}

	logging("${device} : Preferences Updated", "info")

	loggingStatus()

}


void refresh() {

	sendZigbeeCommands(["he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x0005 {}"])
	logging("${device} : Refreshed", "info")

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


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedData = map.data

	if (map.cluster == "0000") {

		if (map.attrId == "0001") {

			updateDataValue("application", map.value)
			logging("${device} : Application : ${map.value}", "debug")

		} else if (map.attrId == "0004") {

			updateDataValue("manufacturer", map.value)
			logging("${device} : Manufacturer : ${map.value}", "debug")

		} else if (map.attrId == "0005") {

			updateDataValue("model", map.value)
			logging("${device} : Model : ${map.value}", "debug")

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "8004") {
		
		if (receivedData[1] == "00") {
			// Received simple descriptor data.

			//updateDataValue("endpointId", receivedData[5])
			updateDataValue("profileId", receivedData[6..7].reverse().join())

			Integer inClusterNum = Integer.parseInt(receivedData[11], 16)
			Integer position = 12
			Integer positionCounter = null
			String inClusters = ""
			if (inClusterNum > 0) {
				(1..inClusterNum).each() {b->
					positionCounter = position+((b-1)*2)
					inClusters += receivedData[positionCounter..positionCounter+1].reverse().join()
					if (b < inClusterNum) {
						inClusters += ","
					}
				}
			}
			position += inClusterNum*2
			Integer outClusterNum = Integer.parseInt(receivedData[position], 16)
			position += 1
			String outClusters = ""
			if (outClusterNum > 0) {
				(1..outClusterNum).each() {b->
					positionCounter = position+((b-1)*2)
					outClusters += receivedData[positionCounter..positionCounter+1].reverse().join()
					if (b < outClusterNum) {
						outClusters += ","
					}
				}
			}

			updateDataValue("inClusters", inClusters)
			updateDataValue("outClusters", outClusters)

			logging("${device} : Received $inClusterNum inClusters : $inClusters", "debug")
			logging("${device} : Received $outClusterNum outClusters : $outClusters", "debug")

		} else {

        	reportToDev(map)

		}

	} else {

		reportToDev(map)

	}

}


// Library


void sendZigbeeCommands(List<String> cmds) {
	// All hub commands go through here for immediate transmission and to avoid some method() weirdness.

    logging("${device} : sendZigbeeCommands received : ${cmds}", "trace")
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))

}


void updatePresence() {

	long millisNow = new Date().time
	state.presenceUpdated = millisNow
	sendEvent(name: "presence", value: "present")

}


void checkPresence() {
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


void loggingStatus() {

	log.info  "${device} :  Info Logging : ${infoLogging == true}"
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
	
	log.info "${device} : Info  Logging : Automatically Disabled"
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
