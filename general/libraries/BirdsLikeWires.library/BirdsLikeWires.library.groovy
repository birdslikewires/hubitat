/*
 * 
 *  BirdsLikeWires Library v1.46 (28th August 2025)
 *	
 */


library (

	author: "Andrew Davison",
	category: "zigbee",
	description: "Library methods used by BirdsLikeWires drivers.",
	documentationLink: "https://github.com/birdslikewires/hubitat",
	name: "library",
	namespace: "BirdsLikeWires"

)


void sendZigbeeCommands(List<String> cmds) {
	// All hub commands go through here for immediate transmission and to avoid some method() weirdness.

    logging("${device} : sendZigbeeCommands received : ${cmds}", "trace")
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))

}


void installed() {

	// Runs after first installation.
	long millis = now()
	updateDataValue("installed", "${millis}")
	logging("${device} : Installed", "info")
	configure()

}


void configure() {

	// Tidy up.
	unschedule()
	state.clear()

	// Set up health status.
	state.updatedHealthStatus = 0
	sendEvent(name: "healthStatus", value: "online", isStateChange: false)

	// Schedule health status checking.
	int randomSixty = Math.abs(new Random().nextInt() % 60)
	int reportIntervalMinutesClamped = Math.min(Math.max(1, reportIntervalMinutes), 59)
	schedule("${randomSixty} 0/${reportIntervalMinutesClamped} * * * ? *", checkHealthStatus)

	// Set device specifics.
	updateDataValue("driver", "$driverVersion")
	configureSpecifics()

	// Notify.
	String encoding = getDataValue("encoding")
	if ("$encoding" != "MQTT") {
		sendEvent(name: "configuration", value: "sent", isStateChange: false)
		logging("${device} : Configuration : Sent to device.", "info")
	} else {
		updateDataValue("isComponent", "false")
		sendEvent(name: "configuration", value: "set", isStateChange: false)
		logging("${device} : Configuration : Set.", "info")
	}

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

	updateSpecifics()

	logging("${device} : Preferences updated.", "info")

	loggingStatus()

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


void levelChange(int multiplier) {

	levelChange(multiplier, "")

}


void levelChange(int multiplier, String direction) {
	// Work out the level we should report based upon a hold duration.

	long millisActive = now() - state.levelChangeStart
	if (millisActive > 6000) {
		millisActive = 0				// In case we don't receive a 'released' message.
	}

	int levelChange = millisActive / 6000 * multiplier
	// That multiplier above is arbitrary - just use whatever feels right when testing the device.
	// The greater the multiplier, the quicker the level value will increase. A larger value is better for laggier devices.

	BigDecimal secondsActive = millisActive / 1000
	secondsActive = secondsActive.setScale(2, BigDecimal.ROUND_HALF_UP)

	logging("${device} : Level : Change of ${levelChange} after action for ${secondsActive} seconds.", "info")

	levelEvent(levelChange, direction)

}


void levelEvent(int levelChange, String direction) {

	int initialLevel = device.currentState("level") ? device.currentState("level").value.toInteger() : 0

	int newLevel = 0

	if ("$direction" == "decrease") {

		newLevel = device.currentState("level").value.toInteger() - levelChange
		levelChange *= -1

	} else if ("$direction" == "increase") {

		newLevel = device.currentState("level").value.toInteger() + levelChange

	} else {

		newLevel = levelChange

	}

	newLevel = newLevel <= 100 ? newLevel : 100
	newLevel = newLevel < 0 ? 0 : newLevel

	logging("${device} : levelEvent : Got level of '${levelChange}', sending ${newLevel}%", "debug")

	sendEvent(name: "level", value: newLevel)

}


void updateHealthStatus() {

	long millisNow = new Date().time
	state.updatedHealthStatus = millisNow
	sendEvent(name: "healthStatus", value: "online")

}


void checkHealthStatus() {
	// Check how long ago the health status was updated.

	long millisNow = new Date().time
	int uptimeAllowanceMinutes = 20			// Busy hubs can take a while to settle after a reboot.

	if (state.updatedHealthStatus > 0) {

		long millisElapsed = millisNow - state.updatedHealthStatus
		long timeoutMillis = ((reportIntervalMinutes * 2) + 10) * 60000
		long reportIntervalMillis = reportIntervalMinutes * 60000
		BigInteger secondsElapsed = BigDecimal.valueOf(millisElapsed / 1000)
		BigInteger hubUptime = location.hub.uptime

		if (millisElapsed > timeoutMillis) {

			if (hubUptime > uptimeAllowanceMinutes * 60) {

				sendEvent(name: "healthStatus", value: "offline")
				logging("${device} : Health Status : Last report received ${secondsElapsed} seconds ago.", "warn")

			} else {

				logging("${device} : Health Status : Ignoring overdue reports for ${uptimeAllowanceMinutes} minutes. The hub was rebooted ${hubUptime} seconds ago.", "debug")

			}

		} else {

			sendEvent(name: "healthStatus", value: "online")
			logging("${device} : Health Status : Last report received ${secondsElapsed} seconds ago.", "debug")

		}

		logging("${device} : checkHealthStatus() : ${millisNow} - ${state.updatedHealthStatus} = ${millisElapsed}", "trace")
		logging("${device} : checkHealthStatus() : Report interval is ${reportIntervalMillis} ms, timeout is ${timeoutMillis} ms.", "trace")

	} else {

		logging("${device} : Health Status : Waiting for first report.", "warn")

	}

}


void checkDriver() {

	String versionCheck = "unknown"
	versionCheck = "${getDeviceDataByName('driver')}"

	if ("$versionCheck" != "$driverVersion") {

		logging("${device} : Driver : Updating configuration from $versionCheck to $driverVersion.", "info")
		configure()

	}

}


void requestBasic() {

	// Request manufacturer, cluster library version, application version, model name,
	// power source and Tuya's "attributeReportingStatus" from the Basic cluster.
	// This is mostly information we want anyway, but we request it in "Tuya Magic Spell" format as
	// this may be advantageous in putting some of their devices into the correct mode.
	ArrayList<String> cmds = []
	cmds += zigbee.readAttribute(0x0000, [0x0004, 0x0000, 0x0001, 0x0005, 0x0007, 0xfffe])
	sendZigbeeCommands(cmds)
	pauseExecution(500)

	// Request software build and simple descriptor data.
	sendZigbeeCommands([

		"he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x4000 {}",
		"he raw ${device.deviceNetworkId} 0x0000 0x0000 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} 01} {0x0000}"

	])

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

	if (map.command == "07") {

		if (map.data[0] == "00") {

			sendEvent(name: "configuration", value: "ok", isStateChange: false)
			logging("${device} : Configuration : Received and accepted by device.", "info")

		} else {

			sendEvent(name: "configuration", value: "failed", isStateChange: false)
			logging("${device} : Configuration : Device may not have processed configuration correctly.", "warn")

		}

	} else {

		reportToDev(map)

	}

}


void processDescriptors(Map map) {
	// Process the simple descriptors normally received from Zigbee Cluster 8004 into device data values.

	if (map.data[1] == "00") {
		// Received simple descriptor data.

		//updateDataValue("endpointId", map.data[5])						// can lead to a weird duplicate
		updateDataValue("profileId", map.data[6..7].reverse().join())

		Integer inClusterNum = Integer.parseInt(map.data[11], 16)
		Integer position = 12
		Integer positionCounter = null
		String inClusters = ""
		if (inClusterNum > 0) {
			(1..inClusterNum).each() {b->
				positionCounter = position+((b-1)*2)
				inClusters += map.data[positionCounter..positionCounter+1].reverse().join()
				if (b < inClusterNum) {
					inClusters += ","
				}
			}
		}
		position += inClusterNum*2
		Integer outClusterNum = Integer.parseInt(map.data[position], 16)
		position += 1
		String outClusters = ""
		if (outClusterNum > 0) {
			(1..outClusterNum).each() {b->
				positionCounter = position+((b-1)*2)
				outClusters += map.data[positionCounter..positionCounter+1].reverse().join()
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


void reportBattery(String batteryVoltageHex, int batteryVoltageDivisor, BigDecimal batteryVoltageScaleMin, BigDecimal batteryVoltageScaleMax) {
	// Report the battery voltage and calculated percentage.

	if ($batteryVoltageHex == "") {
		// Ignore empty nonsense.
		logging("${device} : batteryVoltageHex : skipping anomolous reading.", "debug")
		return
	}

	BigDecimal batteryVoltage = 0

	logging("${device} : batteryVoltageHex : ${batteryVoltageHex}", "trace")

	batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex)
	logging("${device} : batteryVoltage raw value : ${batteryVoltage}", "debug")

	batteryVoltage = batteryVoltage / batteryVoltageDivisor
	batteryVoltage = batteryVoltage.setScale(3, BigDecimal.ROUND_HALF_UP)

	logging("${device} : batteryVoltage : ${batteryVoltage}", "debug")
	sendEvent(name: "voltage", value: batteryVoltage, unit: "V")

	BigDecimal batteryPercentage = 0

	if (batteryVoltage >= batteryVoltageScaleMin) {

		batteryPercentage = ((batteryVoltage - batteryVoltageScaleMin) / (batteryVoltageScaleMax - batteryVoltageScaleMin)) * 100.0
		batteryPercentage = batteryPercentage.setScale(0, BigDecimal.ROUND_HALF_UP)
		batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage
		batteryPercentage = batteryPercentage < 0 ? 0 : batteryPercentage

		if (batteryPercentage > 20) {
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "info")
		} else {
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
		}

		sendEvent(name: "battery", value:batteryPercentage, unit: "%")
		state.battery = "discharging"

	} else {

		// Very low voltages indicate an exhausted battery which requires replacement.

		batteryPercentage = 0

		logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
		logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
		sendEvent(name: "battery", value:batteryPercentage, unit: "%")
		state.battery = "exhausted"

	}

}


void reportToDev(map) {

	if (map.endpoint == null) return		// If the message doesn't even have an endpoint, it's garbage. Discard!

	if (map.data != null) String dataCount = "${map.data.length} bits of "

	logging("${device} : UNKNOWN DATA! Please report these messages to the developer.", "warn")
	logging("${device} : Received : endpoint: ${map.endpoint}, cluster: ${map.cluster}, clusterId: ${map.clusterId}, attrId: ${map.attrId}, command: ${map.command} with value: ${map.value} and ${dataCount}data: ${map.data}", "warn")
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


void withDebounce(String id, long debouncePeriod, Closure closure) {

    if (!state.debounceTimestamps) state.debounceTimestamps = [:]

    def lastExecutionTime = state.debounceTimestamps[id] ?: 0
    def currentTime = now()

    if (currentTime - lastExecutionTime >= debouncePeriod) {

        state.debounceTimestamps[id] = currentTime
        closure.call()

    } else {

        logging("${device} : Action : '$id' DEBOUNCED", "debug")

    }

}


def fetchChild(String namespace, String type, String ident) {
	// Creates and retrieves child devices.

	// Namespace is required for custom child drivers. Use "hubitat" for system drivers.
	// Type will determine the driver to use.
	// Ident is any unique identifier.

	def childDevice = getChildDevice("${device.id}-${ident}")
	def childInitialLabel = (ident.contains("-")) ? ident.split("-").last() : ident

	if (ident != "null") {

		if (!childDevice) {

			logging("${device} : Creating child device $device.id-$ident", "debug")

			childDevice = addChildDevice("${namespace}", "${type}", "${device.id}-${ident}", [name: "${type}", label: "${childInitialLabel}", isComponent: false])

		}

		logging("${device} : Retrieved child device $device.id-$ident", "debug")

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


private String[] millisToDhms(BigInteger millisToParse) {

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


private String hexToBinary(String thisByte, Integer size = 8) {

	String binaryValue = new BigInteger(thisByte, 16).toString(2);
	return String.format("%${size}s", binaryValue).replace(' ', '0')
	
}


private Integer hexToPercentage(String hex) {

	String safeHex = hex.take(2)
    Integer pc = Integer.parseInt(safeHex, 16) << 21 >> 21
	return pc / 2.55

}


private String hexToText(String hex) {

	String text = ""
	int pos = 0
	while(pos < hex.length() - 1) {
		text = text + (char)Integer.parseInt(hex.substring(pos, pos + 2), 16)
		pos += 2
	}
	return text.trim()

}


private String percentageToHex(Integer pc) {

	BigDecimal safePc = pc > 0 ? (pc*2.55) : 0
	safePc = safePc > 255 ? 255 : safePc
	return Integer.toHexString(safePc.intValue())

}


private Integer percentageToOctet(Integer pc) {

	Integer octet = Math.round(pc * 2.54).toInteger()
	octet = octet > 254 ? 254 : octet
	return octet

}


private Integer octetToPercentage(Integer octet) {

	Integer pc = Math.round(octet / 2.54).toInteger()
	pc = pc > 100 ? 100 : pc
	return pc

}


private Integer kelvinToMired(Integer kelvin) {

	Integer mired = Math.round(1000000 / kelvin).toInteger()
	mired = mired > 454 ? 454 : mired
	mired = mired < 250 ? 250 : mired
	return mired

}


private Integer miredToKelvin(Integer mired) {

	Integer kelvin = Math.round(1000000 / mired).toInteger()
	return kelvin

}


private String capitaliseFirstLetters(String input) {

    if (input == null || input.isEmpty()) return input

    String[] words = input.split("\\s+")
    StringBuilder output = new StringBuilder(input.length())

    for (String word : words) {

        output.append(Character.toUpperCase(word.charAt(0)))

        if (word.length() > 1) {
            output.append(word.substring(1))
        }

        output.append(" ")

    }

    return output.toString().trim()

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


void filterThis(Map map) {
	// Everything that hasn't been caught or rejected ends up in this filter.

	if (map.clusterId == "0001") {

		processConfigurationResponse(map)

	} else if (map.clusterId == "0006") {

		logging("${device} : Skipped : Match Descriptor Request", "debug")

	} else if (map.clusterId == "0013") {

		logging("${device} : Received : Device Announce Broadcast", "debug")
		refresh()

	} else if (map.clusterId == "0400") {

		processConfigurationResponse(map)

	} else if (map.cluster == "8001" || map.clusterId == "8001") {

		logging("${device} : Skipped : Network Address Response", "debug")

	} else if (map.clusterId == "8004") {
		
		processDescriptors(map)

	} else if (map.clusterId == "8005") {

		logging("${device} : Skipped : Active End Point Response", "debug")

	} else if (map.clusterId == "8021") {

		logging("${device} : Skipped : Bind Response", "debug")

	} else if (map.cluster == "8032" || map.clusterId == "8032") {

		logging("${device} : Skipped : Routing Response", "debug")

	} else if (map.cluster == "8034" || map.clusterId == "8034") {

		logging("${device} : Skipped : Leave Response", "debug")

	} else if (map.cluster == "8038" || map.clusterId == "8038") {

		logging("${device} : Skipped : Network Update", "debug")

	} else if (map.cluster == null && map.clusterId == null) {

		logging("${device} : Skipped : Empty Message", "debug")

	} else if (map.cluster == "0000") {

		processBasic(map)

	} else {

		reportToDev(map)

	}

}


void mqttConnect() {

	try {

		def mqttInt = interfaces.mqtt

		if (mqttInt.isConnected()) {
			logging("${device} : mqttConnect : Connection to broker ${state.mqttBroker} (${state.mqttTopic}) is live.", "trace")
			return
		}

		if (state.mqttTopic == "") {
			logging("${device} : mqttConnect : Topic is not set.", "error")
			return
		}

		String clientID = "hubitat-" + device.deviceNetworkId
		mqttBrokerUrl = "tcp://" + state.mqttBroker + ":1883"
		mqttInt.connect(mqttBrokerUrl, clientID, settings?.mqttUser, settings?.mqttPass)
		pauseExecution(500)
		mqttInt.subscribe(state.mqttTopic)

	} catch (Exception e) {

		if (state.mqttBroker == null) {

			logging("${device} : mqttConnect : No broker configured.", "warn")

		} else {

			logging("${device} : mqttConnect : ${e.message}", "error")

		}

	}

} 


void mqttClientStatus(String status) {

	if (status.indexOf('Connection succeeded') >= 0) {

		logging("${device} : mqttClientStatus : Connection to broker ${state.mqttBroker} (${state.mqttTopic}) is live.", "trace")

	} else {

		logging("${device} : mqttClientStatus : ${status}", "error")

	}

}


void mqttProcessBasics(def json) {
	// These values are always present in a Zigbee2MQTT device message.

	sendEvent(name: "lqi", value: "${json.linkquality}".toInteger())
	String powerSource = "${json.device.powerSource}".toLowerCase().contains("mains") ? "mains" : "battery"
	sendEvent(name: "powerSource", value:"$powerSource")

	device.label = "${json.device.friendlyName}"

	updateDataValue("ieee", "${json.device.ieeeAddr}")
	updateDataValue("manufacturer", "${json.device.manufacturerName}")
	updateDataValue("model", "${json.device.model}")

}


def mqttGetStateType() {

	def details = "${device.deviceNetworkId}".split('-')
	String stateType = ("${details[-2]}" > 1) ? "state_l${details[-1]}" : "state"
	return stateType

}
