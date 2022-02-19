/*
 * 
 *  Xiaomi Mijia Smart Light Sensor GZCGQ01LM Driver v1.07 (19th February 2022)
 *	
 */


import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 60
@Field int luxTolerance = 200


metadata {

	definition (name: "Xiaomi Mijia Smart Light Sensor GZCGQ01LM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_mijia_smart_light_sensor_gzcgq01lm.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "IlluminanceMeasurement"
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
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "LUMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "GZCGQ01LM"
		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "XIAOMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "GZCGQ01LM"

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

	// Tidy up.
	unschedule()

	state.clear()
	state.presenceUpdated = 0
	state.rawLux = 0
	
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Set default preferences.
	device.updateSetting("infoLogging", [value: "true", type: "bool"])

	// Configure device reporting.
	int reportIntervalMinSeconds = 3
	int reportIntervalMaxSeconds = reportIntervalMinutes * 60

	ArrayList<String> cmds = [
		"zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0000 {${device.zigbeeId}} {}",
		"zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}",
		"zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0003 {${device.zigbeeId}} {}",
		"zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0400 {${device.zigbeeId}} {}",
	]
	cmds += zigbee.configureReporting(0x0001, 0x0020, 0x20, reportIntervalMaxSeconds, reportIntervalMaxSeconds, null)
    cmds += zigbee.configureReporting(0x0400, 0x0000, 0x21, reportIntervalMinSeconds, reportIntervalMaxSeconds, luxTolerance)
	sendZigbeeCommands(cmds)
 
	// Schedule presence checking.
 	int randomSixty
	int checkEveryMinutes = 10					
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Set device name.
	device.name = "Xiaomi Mijia Smart Light Sensor GZCGQ01LM"

	// Notify.
	sendEvent(name: "configuration", value: "set", isStateChange: false)
	logging("${device} : Configuration : Hub settings complete.", "info")

	updated()

}


void updated() {
	// Runs when preferences are saved.

	unschedule(infoLogOff)
	unschedule(debugLogOff)
	unschedule(traceLogOff)

	if (!debugMode) {
		runIn(2400,debugLogOff)
		runIn(1200,traceLogOff)
	}

	logging("${device} : Preferences Updated", "info")

	loggingStatus()

}


void push(buttonId) {
	
	sendEvent(name:"pushed", value: buttonId, isStateChange:true)
	
}


void parse(String description) {

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


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedValue = map.value

	if (map.cluster == "0000") {

		// processBasic(map)
		reportToDev(map)

	} else if (map.cluster == "0001") { 

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

			reportToDev(map)

		}

	} else if (map.cluster == "0400") {

		// Illuminance data received.

		Integer lux = Integer.parseInt(map.value,16)
		Integer luxVariance = Math.abs(state.rawLux - lux)

		if (state.rawLux == null || luxVariance > luxTolerance) {

			state.rawLux = lux
			lux = lux > 0 ? Math.round(Math.pow(10,(lux/10000)) - 1) : 0

			def lastLux = device.currentState("illuminance").value.toInteger()
			String illuminanceDirection = lux > lastLux ? "brightening" : "darkening"
			String illuminanceDirectionLog = illuminanceDirection.capitalize()

			logging("${device} : Lux : ${illuminanceDirectionLog} from ${lastLux} to ${lux} lux.", "debug")
			sendEvent(name: "illuminance", value: lux, unit: "lux")
			sendEvent(name: "illuminanceDirection", value: "${illuminanceDirection}")
			sendEvent(name: "illuminanceWithUnit", value: "${lux} lux")

		} else {

			logging("${device} : Lux : Variance of ${luxVariance} (previously ${state.rawLux}, now ${lux}) is within tolerance.", "debug")

		}

	} else if (map.clusterId == "0001") {

		processConfigurationResponse(map)

	} else if (map.clusterId == "0003") {

		if (map.command == "01") {

			// Scrounge more value! We can capture a short press of the reset button and make it useful.
			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "0013") {

		logging("${device} : Skipped : Device Announce Broadcast", "debug")

	} else if (map.clusterId == "0400") {

		processConfigurationResponse(map)

	} else if (map.clusterId == "8004") {
		
		processDescriptors(map)

	} else if (map.clusterId == "8005") {

		logging("${device} : Skipped : Active End Point Response", "debug")

	} else if (map.clusterId == "8021") {

		logging("${device} : Skipped : Bind Response", "debug")

	} else {

		reportToDev(map)

	}

}


// Library v1.02 (19th February 2022)


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

	}

}


void processConfigurationResponse(Map map) {

	String[] receivedData = map.data

	if (map.command == "07") {

		if (receivedData[0] == "00") {

			sendEvent(name: "configuration", value: "received", isStateChange: false)
			logging("${device} : Configuration : Received by device.", "info")

		} else {

			logging("${device} : Configuration : Device may not have processed configuration correctly.", "warn")

		}

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
	logging("${device} : Received : endpoint: ${map.endpoint}, cluster: ${map.cluster}, clusterId: ${map.clusterId}, attrId: ${map.attrId}, command: ${map.command} with value: ${map.value} and ${receivedDataCount}data: ${receivedData}", "warn")
	logging("${device} : Splurge! : ${map}", "trace")

}


@Field static Boolean debouncingParentState = false
void debounceParentState(String attribute, String state, String message, String level, Integer duration) {

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


void deleteChildren() {
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


private String flipLittleEndian(Map map, String attribute) {

	String bigEndianAttribute = ""
	for (int v = map."${attribute}".length(); v > 0; v -= 2) {
		bigEndianAttribute += map."${attribute}".substring(v - 2, v)
	}
	return bigEndianAttribute

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


private String hexToBinary(String thisByte, Integer size = 8) {

	String binaryValue = new BigInteger(thisByte, 16).toString(2);
	return String.format("%${size}s", binaryValue).replace(' ', '0')
	
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
