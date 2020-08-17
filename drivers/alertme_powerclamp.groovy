/*
 * 
 *  AlertMe Power Clamp Driver v1.05 (16th August 2020)
 *	
 */


metadata {

	definition (name: "AlertMe Power Clamp", namespace: "AlertMe", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme_powerclamp.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "EnergyMeter"
		capability "Initialize"
		capability "PowerMeter"
		capability "PowerSource"
		capability "PresenceSensor"
		capability "Refresh"
		capability "SignalStrength"
		capability "TemperatureMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "string"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "energyWithUnit", "string"
		attribute "mode", "string"
		attribute "powerWithUnit", "string"
		attribute "temperatureWithUnit", "string"
		attribute "uptime", "string"
		attribute "uptimeReadable", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00EF", outClusters: "", manufacturer: "AlertMe.com", model: "Power Clamp", deviceJoinName: "AlertMe Power Clamp"
		fingerprint profileId: "C216", inClusters: "00F0,00F3,00EF", outClusters: "", manufacturer: "AlertMe.com", model: "Power Clamp Device", deviceJoinName: "AlertMe Power Clamp"

	}

}


preferences {
	
	input name: "sensorCorrection", type: "decimal", title: "Sensor Correction Multiplier", defaultValue: 1.00

	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


def installed() {
	// Runs after first pairing.
	logging("${device} : Installing", "info")
}


def initialize() {

	// Runs on reboot if in capabilities list.

	logging("${device} : Initialising", "info")

	// Reset states a few states.
	state.presenceUpdated = 0
	state.rangingPulses = 0
	sendEvent(name: "rssi", value: 0)	// Not found this in reports from AlertMe devices.

	// Remove any old state variables.
	state.remove("batteryInstalled")
	state.remove("firmwareVersion")	
	state.remove("uptime")
	state.remove("uptimeReceived")
	state.remove("presentAt")
	state.remove("relayClosed")
	state.remove("rssi")
	state.remove("supplyPresent")

	// Remove any old device details.
	removeDataValue("application")

	// Stagger our device refresh or we run the risk of DDoS attacking ourselves!
	randomValue = Math.abs(new Random().nextInt() % 30)
	runIn(randomValue,refresh)

}


def configure() {
	// Runs after installed() whenever a device is paired or rejoined.
	logging("${device} : Configuring", "info")

	state.batteryOkay = true
	state.operatingMode = "normal"
	state.presenceUpdated = 0
	state.rangingPulses = 0

	device.updateSetting("infoLogging",[value:"true",type:"bool"])
	device.updateSetting("debugLogging",[value:"false",type:"bool"])
	device.updateSetting("traceLogging",[value:"false",type:"bool"])

	// Remove any scheduled events.
	unschedule()

	// Bunch of zero or null values.
	sendEvent(name: "battery",value:0, unit: "%", isStateChange: false)
	sendEvent(name: "batteryState",value: "unknown", isStateChange: false)
	sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "batteryWithUnit", value: "unknown",isStateChange: false)
	sendEvent(name: "energy", value: 0, unit: "kWh", isStateChange: false)
	sendEvent(name: "energyWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "lqi", value: 0)
	sendEvent(name: "mode", value: "unknown",isStateChange: false)
	sendEvent(name: "power", value: 0, unit: "W", isStateChange: false)
	sendEvent(name: "powerSource", value: "unknown", isStateChange: false)
	sendEvent(name: "powerWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "presence", value: "not present")
	sendEvent(name: "stateMismatch",value: true, isStateChange: false)
	sendEvent(name: "switch", value: "unknown")
	sendEvent(name: "temperature", value: 0, unit: "C", isStateChange: false)
	sendEvent(name: "temperatureWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "uptime", value: 0, unit: "s", isStateChange: false)
	sendEvent(name: "uptimeReadable", value: "unknown")

	// Schedule our ranging report.
	randomValue = Math.abs(new Random().nextInt() % 60)
	schedule("${randomValue} ${randomValue}/59 * * * ? *", rangeAndRefresh)		// At X seconds past the minute, every 59 minutes, starting at X minutes past the hour.

	// Schedule the presence check.
	randomValue = Math.abs(new Random().nextInt() % 60)
	schedule("${randomValue} 0/5 * * * ? *", checkPresence)						// At X seconds past the minute, every 5 minutes.

	// Set the operating mode and turn off advanced logging.
	rangingMode()
	runIn(6,normalMode)

	// All done.
	logging("${device} : Configured", "info")
	
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
	logging("${device} : Received cluster: ${map.cluster}, clusterId: ${map.clusterId}, attrId: ${map.attrId}, command: ${map.command} with value: ${map.value} and ${receivedDataCount}data: ${receivedData}", "warn")
	logging("${device} : Splurge! ${map}", "warn")

}


def normalMode() {

	// This is the standard, quite chatty, running mode of the outlet.

	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F0 {11 00 FA 00 01} {0xC216}"])
	state.operatingMode = "normal"
	refresh()
	sendEvent(name: "mode", value: "normal")
	logging("${device} : Mode : Normal", "info")

}


def rangingMode() {

	// Ranging mode double-flashes (good signal) or triple-flashes (poor signal) the indicator
	// while reporting LQI values. It's also a handy means of identifying or pinging a device.

	// Don't set state.operatingMode here! Ranging is a temporary state only.

	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F0 {11 00 FA 01 01} {0xC216}"])
	sendEvent(name: "mode", value: "ranging")
	logging("${device} : Mode : Ranging", "info")

	// Ranging will be disabled after a maximum of 30 pulses.
	state.rangingPulses = 0

}


def quietMode() {

	// Turns off all reporting except for a ranging message every 2 minutes.
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F0 {11 00 FA 03 01} {0xC216}"])
	state.operatingMode = "quiet"
	refresh()
	sendEvent(name: "battery",value:0, unit: "%", isStateChange: false)
	sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "batteryWithUnit", value: "unknown",isStateChange: false)
	sendEvent(name: "energy", value: 0, unit: "kWh", isStateChange: false)
	sendEvent(name: "energyWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "mode", value: "quiet")
	sendEvent(name: "power", value: 0, unit: "W", isStateChange: false)
	sendEvent(name: "powerWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "uptime", value: 0, unit: "s", isStateChange: false)
	sendEvent(name: "uptimeReadable", value: "unknown")
	sendEvent(name: "temperature", value: 0, unit: "C", isStateChange: false)
	sendEvent(name: "temperatureWithUnit", value: "unknown", isStateChange: false)
	logging("${device} : Mode : Quiet", "info")

}


void refresh() {

	logging("${device} : Refreshing", "info")
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F6 {11 00 FC 01} {0xC216}"])	   // version information request

}


def rangeAndRefresh() {

	// This toggles ranging mode to update the device's LQI value.
	// On return to the operating mode, refresh() is called by the whateverMode() method to keep remote control active.

	rangingMode()
	runIn(3, "${state.operatingMode}Mode")

}


def updatePresence() {

	long millisNow = new Date().time
	state.presenceUpdated = millisNow

}


def checkPresence() {

	// Check how long ago the last presence report was received.

	// In normal mode the outlets report uptime and power every 10 seconds.
	// In quiet mode the outlets only send a ranging report every 30 seconds.

	long millisNow = new Date().time

	presenceTimeoutMinutes = 5

	if (state.presenceUpdated > 0) {

		long millisElapsed = millisNow - state.presenceUpdated
		long presenceTimeoutMillis = presenceTimeoutMinutes * 60000
		BigDecimal secondsElapsed = millisElapsed / 1000

		if (millisElapsed > presenceTimeoutMillis) {

			sendEvent(name: "battery",value:0, unit: "%", isStateChange: false)
			sendEvent(name: "batteryState",value: "unknown", isStateChange: false)
			sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
			sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
			sendEvent(name: "lqi", value: 0)
			sendEvent(name: "presence", value: "not present")
			sendEvent(name: "temperature", value: 0, unit: "C", isStateChange: false)
			sendEvent(name: "temperatureWithUnit", value: "unknown", isStateChange: false)
			logging("${device} : Not Present : Last presence report ${secondsElapsed} seconds ago.", "warn")

		} else {

			sendEvent(name: "presence", value: "present")
			logging("${device} : Present : Last presence report ${secondsElapsed} seconds ago.", "debug")

		}

		logging("${device} : checkPresence() : ${millisNow} - ${state.presenceUpdated} = ${millisElapsed} (Threshold: ${presenceTimeoutMillis})", "trace")

	} else {

		logging("${device} : checkPresence() : Waiting for first presence report.", "debug")

	}

}


def parse(String description) {

	// Primary parse routine.

	logging("${device} : Parse : $description", "debug")

	sendEvent(name: "presence", value: "present", isStateChange: false)
	updatePresence()

	def descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Failed to create description map from received data.", "warn")

	}	

}


def processMap(map) {

	logging("${device} : processMap() : ${map}", "trace")

	// AlertMe values are always sent in a data element.
	String[] receivedData = map.data

	if (map.clusterId == "00EF") {

		// Power and energy messages.

		BigDecimal sensorCorrectionMultiplier = sensorCorrection.toBigDecimal()

		if (map.command == "81") {

			// Power Reading

			def powerValueHex = "undefined"
			BigDecimal powerValue = 0

			// These power readings are so frequent that we only log them in debug or trace.
			powerValueHex = receivedData[0..1].reverse().join()
			logging("${device} : power byte flipped : ${powerValueHex}", "trace")
			powerValue = zigbee.convertHexToInt(powerValueHex)
			logging("${device} : power sensor reports : ${powerValue}", "debug")

			powerValue = powerValue * sensorCorrectionMultiplier
			powerValue = powerValue.setScale(0, BigDecimal.ROUND_HALF_UP)

			logging("${device} : Power : ${powerValue} W", "info")

			sendEvent(name: "power", value: powerValue, unit: "W", isStateChange: false)
			sendEvent(name: "powerWithUnit", value: "${powerValue} W", isStateChange: false)

		} else if (map.command == "82") {

			// Command 82 returns energy summary in watt-hours with an uptime counter.

			// Energy

			def energyValueHex = "undefined"
			int energyValue = 0

			energyValueHex = receivedData[0..3].reverse().join()
			logging("${device} : energy byte flipped : ${energyValueHex}", "trace")
			energyValue = zigbee.convertHexToInt(energyValueHex)
			logging("${device} : energy counter reports : ${energyValue}", "debug")

			BigDecimal energyValueDecimal = BigDecimal.valueOf(energyValue / 3600 / 1000) * sensorCorrection
			energyValueDecimal = energyValueDecimal.setScale(4, BigDecimal.ROUND_HALF_UP)

			logging("${device} : Energy : ${energyValueDecimal} kWh", "info")

			sendEvent(name: "energy", value: energyValueDecimal, unit: "kWh", isStateChange: false)
			sendEvent(name: "energyWithUnit", value: "${energyValueDecimal} kWh", isStateChange: false)

			// Uptime

			def uptimeValueHex = "undefined"
			int uptimeValue = 0

			uptimeValueHex = receivedData[4..8].reverse().join()
			logging("${device} : uptime byte flipped : ${uptimeValueHex}", "trace")
			uptimeValue = zigbee.convertHexToInt(uptimeValueHex)
			logging("${device} : uptime counter reports : ${uptimeValue}", "debug")

			def newDhmsUptime = []
			newDhmsUptime = millisToDhms(uptimeValue * 1000)
			String uptimeReadable = "${newDhmsUptime[3]}d ${newDhmsUptime[2]}h ${newDhmsUptime[1]}m"

			logging("${device} : Uptime : ${uptimeReadable}", "debug")

			sendEvent(name: "uptime", value: uptimeValue, unit: "s", isStateChange: false)
			sendEvent(name: "uptimeReadable", value: uptimeReadable, isStateChange: false)

		} else {

			// Unknown power or energy data.
			reportToDev(map)

		}

	} else if (map.clusterId == "00F0") {

		// Device status, including battery and temperature data.

		// Report the battery voltage and calculated percentage.
		def batteryVoltageHex = "undefined"
		BigDecimal batteryVoltage = 0

		batteryVoltageHex = receivedData[5..6].reverse().join()
		logging("${device} : batteryVoltageHex byte flipped : ${batteryVoltageHex}", "trace")

		batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex) / 1000
		logging("${device} : batteryVoltage sensor value : ${batteryVoltage}", "debug")

		batteryVoltage = batteryVoltage.setScale(3, BigDecimal.ROUND_HALF_UP)

		logging("${device} : batteryVoltage : ${batteryVoltage}", "debug")
		sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V", isStateChange: false)
		sendEvent(name: "batteryVoltageWithUnit", value: "${batteryVoltage} V", isStateChange: false)

		BigDecimal batteryPercentage = 0
		BigDecimal batteryVoltageScaleMin = 2.8
		BigDecimal batteryVoltageScaleMax = 3.1

		if (batteryVoltage >= batteryVoltageScaleMin && batteryVoltage <= 4.4) {

			state.batteryOkay = true

			batteryPercentage = ((batteryVoltage - batteryVoltageScaleMin) / (batteryVoltageScaleMax - batteryVoltageScaleMin)) * 100.0
			batteryPercentage = batteryPercentage.setScale(0, BigDecimal.ROUND_HALF_UP)
			batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage

			if (batteryPercentage > 50) {
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "debug")
			} else if (batteryPercentage > 30) {
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "info")
			} else {
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
			}

			sendEvent(name: "battery", value:batteryPercentage, unit: "%", isStateChange: false)
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %", isStateChange: false)

			if (batteryVoltage > batteryVoltageScaleMax) {
				!state.supplyPresent ?: sendEvent(name: "batteryState", value: "charged", isStateChange: true)
			} else {
				!state.supplyPresent ?: sendEvent(name: "batteryState", value: "charging", isStateChange: true)
			}

		} else if (batteryVoltage < batteryVoltageScaleMin) {

			// Very low voltages indicate an exhausted battery which requires replacement.

			state.batteryOkay = true

			batteryPercentage = 0

			logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%", isStateChange: false)
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %", isStateChange: false)
			sendEvent(name: "batteryState", value: "exhausted", isStateChange: true)

		} else {

			// If the charge circuitry is reporting greater than 4.5 V then the battery is either missing or faulty.
			// THIS NEEDS TESTING ON THE EARLY POWER CLAMP

			state.batteryOkay = false

			batteryPercentage = 0

			logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%", isStateChange: false)
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %", isStateChange: false)
			sendEvent(name: "batteryState", value: "fault", isStateChange: true)

		}

		// Report the temperature in celsius.
		def temperatureValue = "undefined"
		temperatureValue = receivedData[7..8].reverse().join()
		logging("${device} : temperatureValue byte flipped : ${temperatureValue}", "trace")
		def BigDecimal temperatureCelsius = zigbee.convertHexToInt(temperatureValue) / 16
		logging("${device} : temperatureCelsius sensor value : ${temperatureCelsius}", "trace")

		// Smart plugs warm up while being used, so here's how we attempt to correct for this.
		def BigDecimal temperatureCorrectionValue = (state.relayClosed) ? 0.6 : 0.75
		def BigDecimal temperatureCelsiusCorrected = Math.round(temperatureCelsius * temperatureCorrectionValue * 100) / 100
		logging("${device} : temperatureCelsiusCorrected : ${temperatureCelsiusCorrected} = ${temperatureCelsius} x ${temperatureCorrectionValue}", "trace")
		logging("${device} : Corrected Temperature : ${temperatureCelsiusCorrected} C", "debug")
		sendEvent(name: "temperature", value: temperatureCelsiusCorrected, unit: "C", isStateChange: false)
		sendEvent(name: "temperatureWithUnit", value: "${temperatureCelsiusCorrected} °C", isStateChange: false)

	} else if (map.clusterId == "00F2") {

		// Tamper cluster, not normally received from smart plugs.
		reportToDev(map)

	} else if (map.clusterId == "00F3") {

		// Keyfob or Button state change cluster, not normally received from smart plugs.
		reportToDev(map)

	} else if (map.clusterId == "00F6") {

		// Discovery cluster. 

		if (map.command == "FD") {

			// Ranging is our jam, Hubitat deals with joining on our behalf.

			def lqiRangingHex = "undefined"
			def int lqiRanging = 0
			lqiRangingHex = receivedData[0]
			lqiRanging = zigbee.convertHexToInt(lqiRangingHex)
			sendEvent(name: "lqi", value: lqiRanging, isStateChange: false)
			logging("${device} : lqiRanging : ${lqiRanging}", "debug")

			if (receivedData[1] == "77") {

				// This is ranging mode, which must be temporary. Make sure we come out of it.
				state.rangingPulses++
				if (state.rangingPulses > 30) {
					"${state.operatingMode}Mode"()
				}

			} else if (receivedData[1] == "FF") {

				// This is the ranging report received every 30 seconds while in quiet mode.
				logging("${device} : quiet ranging report received", "debug")

			} else if (receivedData[1] == "00") {

				// This is the ranging report received when the device reboots.
				// After rebooting a refresh is required to bring back remote control.
				logging("${device} : reboot ranging report received", "debug")
				refresh()

			} else {

				// Something to do with ranging we don't know about!
				reportToDev(map)

			} 

		} else if (map.command == "FE") {

			// Version information response.

			def versionInfoHex = receivedData[19..receivedData.size() - 1].join()

			StringBuilder str = new StringBuilder()
			for (int i = 0; i < versionInfoHex.length(); i+=2) {
				str.append((char) Integer.parseInt(versionInfoHex.substring(i, i + 2), 16))
			} 

			String versionInfo = str.toString()
			String[] versionInfoBlocks = versionInfo.split("\\s")
			int versionInfoBlockCount = versionInfoBlocks.size()
			String versionInfoDump = versionInfoBlocks[0..versionInfoBlockCount - 1].toString()

			logging("${device} : Version : ${versionInfoBlockCount} Blocks : ${versionInfoDump}", "info")

			String deviceManufacturer = versionInfoBlocks[0].minus(".com")
			String deviceModel = ""
			String deviceFirmware = versionInfoBlocks[versionInfoBlockCount - 1]

			// Sometimes the model name contains spaces.
			if (versionInfoBlockCount == 3) {
				deviceModel = versionInfoBlocks[1]
			} else {
				deviceModel = versionInfoBlocks[1..versionInfoBlockCount - 2].join().toString()
			}

			updateDataValue("manufacturer", deviceManufacturer)
			updateDataValue("model", deviceModel)
			updateDataValue("firmware", deviceFirmware)

		} else {

			// Not a clue what we've received.
			reportToDev(map)

		}

	} else if (map.clusterId == "8001" || map.clusterId == "8038") {

		// These clusters are sometimes received from the SPG100 and I have no idea why.
		//   8001 arrives with 12 bytes of data
		//   8038 arrives with 27 bytes of data
		logging("${device} : Skipping data received on cluserId ${map.clusterId}.", "debug")

	} else if (map.clusterId == "8032" ) {

		// These clusters are sometimes received when joining new devices to the mesh.
		//   8032 arrives with 80 bytes of data, probably routing and neighbour information.
		// We don't do anything with this, the mesh re-jigs itself and is a known thing with AlertMe devices.
		logging("${device} : New join has triggered a routing table reshuffle.", "debug")

	} else {

		// Not a clue what we've received.
		reportToDev(map)

	}

	return null

}


void sendZigbeeCommands(ArrayList<String> cmds) {

	// All hub commands go through here for immediate transmission and to avoid some method() weirdness.

	logging("${device} : sendZigbeeCommands received : $cmds", "trace")

	hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
	cmds.each {

		if (it.startsWith("he raw") == true) {
			allActions.add(it)
		} else if (it.startsWith("delay") == true) {
			allActions.add(new hubitat.device.HubAction(it))
		} else {
			allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
		}

	}

	logging("${device} : sendZigbeeCommands : $cmds", "debug")
	sendHubCommand(allActions)

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
