/*
 * 
 *  Xiaomi Aqara Temperature and Humidity Sensor WSDCGQ11LM Driver v1.05 (21st March 2022)
 *	
 */


import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 60


metadata {

	definition (name: "Xiaomi Aqara Temperature and Humidity Sensor WSDCGQ11LM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_aqara_temperature_humidity_sensor_wsdcgq11lm.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "PressureMeasurement"
		capability "PushableButton"
		capability "RelativeHumidityMeasurement"
		capability "Sensor"
		capability "TemperatureMeasurement"

		attribute "absoluteHumidity", "number"
		attribute "absoluteHumidityWithUnit", "string"
		attribute "batteryState", "string"
		attribute "batteryVoltage", "number"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "humidityWithUnit", "string"
		attribute "pressureDirection", "string"
		//attribute "pressurePrevious", "string"
		attribute "pressureWithUnit", "string"
		attribute "temperatureWithUnit", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,FFFF,0402,0403,0405", outClusters: "0000,0004,FFFF", manufacturer: "LUMI", model: "lumi.weather", deviceJoinName: "WSDCGQ11LM", application: "05"

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
	
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Set default preferences.
	device.updateSetting("infoLogging", [value: "true", type: "bool"])
	device.updateSetting("debugLogging", [value: "${debugMode}", type: "bool"])
	device.updateSetting("traceLogging", [value: "${debugMode}", type: "bool"])

	// Schedule reporting and presence checking.
	int randomSixty
	int checkEveryMinutes = 10					
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Set device name.
	device.name = "Xiaomi Aqara Temperature and Humidity Sensor WSDCGQ11LM"

	// Notify.
	sendEvent(name: "configuration", value: "complete", isStateChange: false)
	logging("${device} : Configuration complete.", "info")

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

	logging("${device} : Parse : $description", "debug")

	updatePresence()

	Map descriptionMap = null

	if (description.indexOf('encoding: 10') >= 0 || description.indexOf('encoding: 20') >= 0) {

		// Normal encoding should bear some resemblance to the Zigbee Cluster Library Specification
		descriptionMap = zigbee.parseDescriptionAsMap(description)

	} else {

		// Anything else is specific to Xiaomi, so we'll just slice and dice the string we receive.
		descriptionMap = description.split(', ').collectEntries {
			entry -> def pair = entry.split(': ')
			[(pair.first()): pair.last()]
		}

	}

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Splurge! : ${description}", "warn")

	}

}


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedValue = map.value

	if (map.cluster == "0402") { 

		// Received temperature data.

		String[] temperatureHex = receivedValue[2..3] + receivedValue[0..1]
		String temperatureFlippedHex = temperatureHex.join()
		BigDecimal temperature = hexStrToSignedInt(temperatureFlippedHex)
		temperature = temperature.setScale(2, BigDecimal.ROUND_HALF_UP) / 100

		logging("${device} : temperature : ${temperature} from hex value ${temperatureFlippedHex} flipped from ${map.value}", "trace")

		String temperatureScale = location.temperatureScale
		if (temperatureScale == "F") {
			temperature = (temperature * 1.8) + 32
		}

		if (temperature > 200 || temperature < -200) {

			logging("${device} : Temperature : Value of ${temperature}°${temperatureScale} is unusual. Watch out for batteries failing on this device.", "warn")

		} else {

			logging("${device} : Temperature : ${temperature} °${temperatureScale}", "info")
			sendEvent(name: "temperature", value: temperature, unit: "${temperatureScale}")
			sendEvent(name: "temperatureWithUnit", value: "${temperature} °${temperatureScale}")

		}

	} else if (map.cluster == "0403") { 

		// Received pressure data.

		String[] pressureHex = receivedValue[2..3] + receivedValue[0..1]
		String pressureFlippedHex = pressureHex.join()
		BigDecimal pressure = hexStrToSignedInt(pressureFlippedHex)
		pressure = pressure.setScale(1, BigDecimal.ROUND_HALF_UP) / 10

		BigDecimal lastPressure = device.currentState("pressure") ? device.currentState("pressure").value.toBigDecimal() : 0

		////////// WORK TO DO - RECORD PREVIOUS PRESSURE AS LASTPRESSURE IF PRESSURE HAS CHANGED OR SOMETHING - TOO TIRED!

		// BigDecimal pressurePrevious = device.currentState("pressurePrevious").value.toBigDecimal()
		// if (pressurePrevious != null && pressure != lastPressure) {
		// 	endEvent(name: "pressurePrevious", value: lastPressure, unit: "kPa")
		// } else if 

		String pressureDirection = pressure > lastPressure ? "rising" : "falling"

		logging("${device} : pressure : ${pressure} from hex value ${pressureFlippedHex} flipped from ${map.value}", "trace")
		logging("${device} : Pressure : ${pressure} kPa", "info")
		sendEvent(name: "pressure", value: pressure, unit: "kPa")
		sendEvent(name: "pressureDirection", value: "${pressureDirection}")
		sendEvent(name: "pressureWithUnit", value: "${pressure} kPa")

	} else if (map.cluster == "0405") { 

		// Received humidity data.

		String[] humidityHex = receivedValue[2..3] + receivedValue[0..1]
		String humidityFlippedHex = humidityHex.join()
		BigDecimal humidity = hexStrToSignedInt(humidityFlippedHex)
		humidity = humidity.setScale(2, BigDecimal.ROUND_HALF_UP) / 100

		logging("${device} : humidity : ${humidity} from hex value ${humidityFlippedHex} flipped from ${map.value}", "trace")

		BigDecimal lastTemperature = device.currentState("temperature") ? device.currentState("temperature").value.toBigDecimal() : 0

		String temperatureScale = location.temperatureScale
		if (temperatureScale == "F") {
			lastTemperature = (lastTemperature - 32) / 1.8
		}

		BigDecimal numerator = (6.112 * Math.exp((17.67 * lastTemperature) / (lastTemperature + 243.5)) * humidity * 2.1674)
		BigDecimal denominator = lastTemperature + 273.15
		BigDecimal absoluteHumidity = numerator / denominator
        absoluteHumidity = absoluteHumidity.setScale(1, BigDecimal.ROUND_HALF_UP)

        String cubedChar = String.valueOf((char)(179))

		if (humidity > 100 || humidity < 0) {

			logging("${device} : Humidity : Value of ${humidity} is out of bounds. Watch out for batteries failing on this device.", "warn")

		} else {

			logging("${device} : Humidity (Relative) : ${humidity} %", "info")
			logging("${device} : Humidity (Absolute) : ${absoluteHumidity} g/m${cubedChar}", "info")
			sendEvent(name: "humidity", value: humidity, unit: "%")
			sendEvent(name: "absoluteHumidity", value: absoluteHumidity, unit: "g/m${cubedChar}")
			sendEvent(name: "humidityWithUnit", value: "${humidity} %")
			sendEvent(name: "absoluteHumidityWithUnit", value: "${absoluteHumidity} g/m${cubedChar}")

		}

	} else if (map.cluster == "0000") {

		if (map.attrId == "0005") {

			// Scrounge more value! We can capture a short press of the reset button and make it useful.
			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else if (map.attrId == "FF01") {

			def deviceData = ""
			def dataSize = map.value.size()

			if (dataSize > 20) {
				deviceData = map.value
			} else {
				logging("${device} : deviceData : No battery information in this report.", "debug")
				return
			}
			
			// Report the battery voltage and calculated percentage.
			def batteryVoltageHex = "undefined"
			BigDecimal batteryVoltage = 0

			batteryVoltageHex = deviceData[8..9] + deviceData[6..7]
			logging("${device} : batteryVoltageHex : ${batteryVoltageHex}", "trace")

			batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex)
			logging("${device} : batteryVoltage sensor value : ${batteryVoltage}", "debug")

			batteryVoltage = batteryVoltage.setScale(2, BigDecimal.ROUND_HALF_UP) / 1000

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

				if (batteryPercentage > 20) {
					logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "info")
				} else {
					logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
				}

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

			// processBasic(map)
			reportToDev(map)

		}

	} else {

		reportToDev(map)

	}

}



// Library v1.03 (20th March 2022) // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //



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


private String[] millisToDhms(long millisToParse) {

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
