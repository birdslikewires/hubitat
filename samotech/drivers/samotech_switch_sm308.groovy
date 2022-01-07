/*
 * 
 *  Samotech Switch SM308 Driver v1.04 (7th January 2022)
 *	
 */


import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 10


metadata {

	definition (name: "Samotech Switch SM308", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/ikea/drivers/samotech_switch_sm308.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "PresenceSensor"
		capability "Refresh"
		capability "Switch"

		command "flash"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		attribute "mode", "string"

		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,1000", outClusters: "0019", manufacturer: "Samotech", model: "SM308", deviceJoinName: "Samotech SM308", application: "00"
		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,1000", outClusters: "0019", manufacturer: "Samotech", model: "SM308-S", deviceJoinName: "Samotech SM308-S", application: "00"
		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0702,0B04,0B05,1000", outClusters: "0019", manufacturer: "Samotech", model: "SM308-2CH", deviceJoinName: "Samotech SM308-2CH", application: "00"

	}

}


preferences {
	
	input name: "flashEnabled", type: "bool", title: "Enable flash", defaultValue: false
	input name: "flashRate", type: "number", title: "Flash rate (ms)", range: "500..5000", defaultValue: 1000

	if ("${getDeviceDataByName('model')}" == "SM308-2CH") {
		input name: "flashRelays", type: "enum", title: "Flash relay", options:[["FF":"Both"],["01":"Relay 1"],["02":"Relay 2"]]
	}

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

	// Tidy up.
	unschedule()

	state.clear()
	state.presenceUpdated = 0

	sendEvent(name: "mode", value: "static", isStateChange: false)
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Set default preferences.
	device.updateSetting("flashEnabled", [value: "false", type: "bool"])
	device.updateSetting("flashRate", [value: 1000, type: "number"])
	device.updateSetting("flashRelays", [value: "", type: "enum"])
	device.updateSetting("infoLogging", [value: "true", type: "bool"])
	device.updateSetting("debugLogging", [value: "${debugMode}", type: "bool"])
	device.updateSetting("traceLogging", [value: "${debugMode}", type: "bool"])

	// Schedule reporting and presence checking.
	int randomSixty

	int reportEveryMinutes = (reportIntervalMinutes < 60) ? reportIntervalMinutes : 59
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${reportEveryMinutes} * * * ? *", refresh)

	int checkEveryMinutes = 10				
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Request application value, manufacturer, model name, software build and simple descriptor data.
	sendZigbeeCommands([
		"he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x0001 {}",
		"he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x0004 {}",
		"he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x0005 {}",
		"he rattr 0x${device.deviceNetworkId} 0x0001 0x0000 0x4000 {}",
		"he raw ${device.deviceNetworkId} 0x0000 0x0000 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} 01} {0x0000}"
	])

	state.relayCount = ("${getDeviceDataByName('model')}" == "SM308-2CH") ? 2 : 1

	if (state.relayCount > 1) {
		// Create child devices.
		for (int i = 1; i == state.relayCount; i++) {
			fetchChild("Switch","0$i")
		}
	} else {
		deleteChildren()
	}

	sendEvent(name: "configuration", value: "success", isStateChange: false)
	logging("${device} : Configured", "info")

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

	logging("${device} : Preferences : Updated", "info")

	loggingStatus()

}


void refresh() {

	sendZigbeeCommands(["he rattr 0x${device.deviceNetworkId} 0xFF 0x0006 0x00 {}"])
	logging("${device} : Refreshed", "info")

}


void off() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x00 {}"])
	sendEvent(name: "mode", value: "static")

}


void on() {

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x01 {}"])
	sendEvent(name: "mode", value: "static")

}


void flash() {

	if (!flashEnabled) {
		logging("${device} : Flash : Disabled", "warn")
		return
	}

	if (!flashRelays && "${getDeviceDataByName('model')}" == "SM308-2CH") {
		logging("${device} : Flash : No relay chosen in preferences.", "warn")
		return
	}

	logging("${device} : Flash : Rate of ${flashRate ?: 1000} ms", "info")
	sendEvent(name: "mode", value: "flashing")
	pauseExecution 200
    flashOn()

}


void flashOn() {

	String mode = device.currentState("mode").value
	logging("${device} : flashOn : Mode is ${mode}", "debug")

    if (mode != "flashing") return
    runInMillis((flashRate ?: 1000).toInteger(), flashOff)

	String flashEndpoint = "FF"
	if (flashRelays) flashEndpoint = flashRelays

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x${flashEndpoint} 0x0006 0x01 {}"])

}


void flashOff() {

	String mode = device.currentState("mode").value
	logging("${device} : flashOn : Mode is ${mode}", "debug")

    if (mode != "flashing") return
	runInMillis((flashRate ?: 1000).toInteger(), flashOn)

	String flashEndpoint = "FF"
	if (flashRelays) flashEndpoint = flashRelays

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x${flashEndpoint} 0x0006 0x00 {}"])

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

void processMap(map) {

	logging("${device} : processMap() : ${map}", "trace")

	if (map.cluster == "0006" || map.clusterId == "0006") {
		// Relay configuration and response handling.
		// State confirmation and command receipts.

		if (map.command == "01") {
			// Relay States (Refresh)

			if (map.value == "00") {

				if (state.relayCount > 1) {

					def childDevice = fetchChild("Switch", "${map.endpoint}")
					childDevice.parse([[name:"switch", value:"off"]])

					def currentChildStates = fetchChildStates("switch","${childDevice.id}")
					logging("${device} : currentChildStates : ${childDevice.id} ${currentChildStates}", "debug")

					if (currentChildStates.every{it == "off"}) {
						logging("${device} : Switch : All Off", "info")
						sendEvent(name: "switch", value: "off")
					}

				} else {

					sendEvent(name: "switch", value: "off")

				}

				logging("${device} : Switch ${map.endpoint} : Off", "info")

			} else {

				if (state.relayCount > 1) {
					def childDevice = fetchChild("Switch", "${map.endpoint}")
					childDevice.parse([[name:"switch", value:"on"]])
				}

				sendEvent(name: "switch", value: "on")
				logging("${device} : Switch ${map.endpoint} : On", "info")

			}

		} else if (map.command == "07") {
			// Relay Configuration

			logging("${device} : Relay Configuration : Successful", "info")

		} else if (map.command == "0A" || map.command == "0B") {
			// Relay States

			// Command "0A" is local actuation, command "0B" is remote actuation.
			// The SM308-2CH (correctly) doesn't send "0A" messages when triggered remotely, but the SM308 and SM308-S do.
			// On the single channel devices we'll use the local "0A" messages as they're essentially a success confirmation.

			// We need to ignore the 'correct' remote actuation response for debouncing on the single channel devices.
			if (state.relayCount == 1 && map.command == "0B") {

				logging("${device} : Skipping remote actuation response in favour of local. Should be through in a few hundred milliseconds.", "debug")
				
			} else {

				String relayActuated = (map.command == "0A") ? map.endpoint : map.sourceEndpoint
				String relayState = (map.command == "0A") ? map.value : map.data[0]

				if (relayState == "00") {

					if (state.relayCount > 1) {

						def childDevice = fetchChild("Switch", "$relayActuated")
						childDevice.parse([[name:"switch", value:"off"]])

						def currentChildStates = fetchChildStates("switch","${childDevice.id}")
						logging("${device} : currentChildStates : ${currentChildStates}", "debug")

						if (currentChildStates.every{it == "off"}) {

							debounceParentState("switch", "off", "All Devices Off", "info", 100)

						}

					} else {

						sendEvent(name: "switch", value: "off")

					}

					logging("${device} : Switched $relayActuated : Off", "info")

				} else {

					if (state.relayCount > 1) {
						def childDevice = fetchChild("Switch", "$relayActuated")
						childDevice.parse([[name:"switch", value:"on"]])
					}

					sendEvent(name: "switch", value: "on")
					logging("${device} : Switched $relayActuated : On", "info")

				}


			}

		} else if (map.command == "00") {

			logging("${device} : skipping state counter message : ${map}", "trace")

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "0702" || map.clusterId == "0702") {

		logging("${device} : Skipping power messaging (unsupported in hardware).", "debug")

	} else if (map.cluster == "8001" || map.clusterId == "8001") {

		logging("${device} : Skipping network address response message.", "debug")

	} else if (map.clusterId == "8004") {
		
		processDescriptors(map)

	} else if (map.cluster == "8021" || map.clusterId == "8021") {

		logging("${device} : Skipping discovery message.", "debug")

	} else if (map.cluster == "8032" || map.clusterId == "8032") {

		logging("${device} : Skipping management routing response message.", "debug")

	} else if (map.cluster == "8034" || map.clusterId == "8034") {

		logging("${device} : Skipping management leave response message.", "debug")

	} else if (map.cluster == "8038" || map.clusterId == "8038") {

		logging("${device} : Skipping management network update notify message.", "debug")

	} else if (map.cluster == "0000") {

		processBasic(map)

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


@Field static Boolean debouncingParentState = false
def debounceParentState(String attribute, String state, String message, String level, Integer duration) {

	if (debouncingParentState) return
	debouncingParentState = true

	sendEvent(name: "$attribute", value: "$state")
	logging("${device} : $message", "$level")

	pauseExecution duration
	debouncingParentState = false

}


def fetchChild(String type, String endpoint) {
	// Creates and retrieves child devices matched to endpoints.

	def childDevice = getChildDevice("${device.id}-${endpoint}")

	if (endpoint != "null") {

		if (!childDevice) {

			logging("${device} : Creating child device $device.id-$endpoint", "debug")

			childDevice = addChildDevice("hubitat", "Generic Component ${type}", "${device.id}-${endpoint}", [name: "${device.displayName} ${type} ${endpoint}", label: "${device.displayName} ${type} ${endpoint}", isComponent: false])

			if (type == "Switch") {

				// We could use this as an opportunity to set all the relays to a known state, but we don't. Just in case.
				childDevice.parse([[name: "switch", value: 'off']])

			} else {

				logging("${device} : fetchChild() : I don't know what to do with the '$type' device type.", "error")

			}

			childDevice.updateSetting("txtEnable", false)

		}

		logging("${device} : Retrieved child device $device.id-$endpoint", "debug")

	} else {

		logging("${device} : Received null endpoint for device $device.id", "error")

	}

	return childDevice

}


def fetchChildStates(String state, String requestor) {
	// Retrieves requested states of child devices.

	logging("${device} : fetchChildStates() : Called by $requestor", "debug")

	def childStates = []
	def children = getChildDevices()

	children.each {child->

		// Give things a chance!
		pauseExecution(100)
	
		// Grabs the requested state from the child device.
		String childState = child.currentValue("${state}")

		if ("${child.id}" != "${requestor}") {
			// Don't include the requestor's state in the results, as we're likely in the process of updating it.
			childStates.add("${childState}")
			logging("${device} : fetchChildStates() : Found $child.id is '$childState'", "debug")
		}

	}

	return childStates

}


def deleteChildren() {
	// Deletes children we may have created.

	logging("${device} : deleteChildren() : Deleting rogue children.", "debug")

	def children = getChildDevices()
    children.each {child->
  		deleteChildDevice(child.deviceNetworkId)
    }

}


void componentRefresh(com.hubitat.app.DeviceWrapper childDevice) {

	logging("componentRefresh() from $childDevice.deviceNetworkId", "debug")
	sendZigbeeCommands(["he rattr 0x${device.deviceNetworkId} 0x${childDevice.deviceNetworkId.split("-")[1]} 0x0006 0x00 {}"])

}


void componentOn(com.hubitat.app.DeviceWrapper childDevice) {

	logging("componentOn() from $childDevice.deviceNetworkId", "debug")
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x${childDevice.deviceNetworkId.split("-")[1]} 0x0006 0x01 {}"])

}


void componentOff(com.hubitat.app.DeviceWrapper childDevice) {

	logging("componentOff() from $childDevice.deviceNetworkId", "debug")
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x${childDevice.deviceNetworkId.split("-")[1]} 0x0006 0x00 {}"])

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


void processBasic(Map map) {
	// Process the basic descriptors normally received from Zigbee Cluster 0000 into device data values.

	if (map.attrId == "0001") {

		updateDataValue("application", "${map.value}")
		logging("${device} : Application : ${map.value}", "debug")

	} else if (map.attrId == "0004") {

		updateDataValue("manufacturer", map.value)
		logging("${device} : Manufacturer : ${map.value}", "debug")

	} else if (map.attrId == "0005") {

		updateDataValue("model", map.value)
		logging("${device} : Model : ${map.value}", "debug")

	} else if (map.attrId == "4000") {

		updateDataValue("softwareBuild", "${map.value}")
		logging("${device} : Firmware : ${map.value}", "debug")

	} else {

		reportToDev(map)

	}

}


void processDescriptors(Map map) {
	// Process the simple descriptors normally received from Zigbee Cluster 8004 into device data values.

	String[] receivedData = map.data

	if (receivedData[1] == "00") {
		// Received simple descriptor data.

		//updateDataValue("endpointId", receivedData[5])						// can lead to a weird duplicate
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

