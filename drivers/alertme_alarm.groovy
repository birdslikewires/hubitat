/*
 * 
 *  AlertMe Alarm Detector Driver v1.05 (27th August 2020)
 *	
 */


import hubitat.zigbee.clusters.iaszone.ZoneStatus


metadata {

	definition (name: "AlertMe Alarm Detector", namespace: "AlertMe", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme_alarm.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "Initialize"
		capability "PresenceSensor"
		capability "Refresh"
		capability "Sensor"
		capability "SignalStrength"
		capability "SoundSensor"
		capability "TamperAlert"
		capability "TemperatureMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "string"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "mode", "string"
		attribute "temperatureWithUnit", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F1,00F2", outClusters: "", manufacturer: "AlertMe.com", model: "PIR Device", deviceJoinName: "AlertMe Alarm Detector"

	}

}


preferences {
	
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

	// Reset states.
	state.presenceUpdated = 0
	state.rangingPulses = 0

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
	sendEvent(name: "batteryState",value: "discharging", isStateChange: false)
	sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "batteryWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "lqi", value: 0, isStateChange: false)
	sendEvent(name: "mode", value: "unknown",isStateChange: false)
	sendEvent(name: "presence", value: "not present", isStateChange: false)
	sendEvent(name: "rssi", value: 0, isStateChange: false)
	sendEvent(name: "sound", value: "not detected", isStateChange: false)
	sendEvent(name: "tamper", value: "clear", isStateChange: false)
	sendEvent(name: "temperature", value: 0, unit: "C", isStateChange: false)
	sendEvent(name: "temperatureWithUnit", value: "unknown", isStateChange: false)

	// Schedule our ranging report.
	randomValue = Math.abs(new Random().nextInt() % 60)
	schedule("${randomValue} ${randomValue}/59 * * * ? *", rangeAndRefresh)		// At X seconds past the minute, every 59 minutes, starting at X minutes past the hour.

	// Schedule the presence check.
	randomValue = Math.abs(new Random().nextInt() % 60)
	schedule("${randomValue} 0/5 * * * ? *", checkPresence)						// At X seconds past the minute, every 5 minutes.

	// Set the operating mode and turn off advanced logging.
	rangingMode()
	runIn(18,normalMode)

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
	logging("${device} : Received : cluster: ${map.cluster}, clusterId: ${map.clusterId}, attrId: ${map.attrId}, command: ${map.command} with value: ${map.value} and ${receivedDataCount}data: ${receivedData}", "warn")
	logging("${device} : Splurge! : ${map}", "trace")

}


def normalMode() {

	// This is the standard running mode.

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

	long millisNow = new Date().time

	presenceTimeoutMinutes = 5

	if (state.presenceUpdated > 0) {

		long millisElapsed = millisNow - state.presenceUpdated
		long presenceTimeoutMillis = presenceTimeoutMinutes * 60000
		BigDecimal secondsElapsed = millisElapsed / 1000

		if (millisElapsed > presenceTimeoutMillis) {

			sendEvent(name: "battery",value:0, unit: "%", isStateChange: false)
			sendEvent(name: "batteryState",value: "discharging", isStateChange: false)
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

	if (description.startsWith("zone status")) {

		ZoneStatus zoneStatus = zigbee.parseZoneStatus(description)
		processStatus(zoneStatus)

	} else {

		Map descriptionMap = zigbee.parseDescriptionAsMap(description)

		if (descriptionMap) {

			processMap(descriptionMap)

		} else {
			
			logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
			logging("${device} : Splurge! : ${description}", "warn")

		}

	}

}


def processStatus(ZoneStatus status) {

	logging("${device} : processStatus() : ${status}", "trace")

	if (status.isAlarm1Set() || status.isAlarm2Set()) {

		logging("${device} : Sound : Detected", "info")
		sendEvent(name: "sound", value: "detected", isStateChange: true)

	} else {

		logging("${device} : Sound : Not Detected", "info")
		sendEvent(name: "sound", value: "not detected", isStateChange: true)

	}

}


def processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	// AlertMe values are always sent in a data element.
	String[] receivedData = map.data

	if (map.clusterId == "00F0") {

		// Device status, including battery and temperature data.

		// Report the battery voltage and calculated percentage.
		def batteryVoltageHex = "undefined"
		BigDecimal batteryVoltage = 0

		batteryVoltageHex = receivedData[5..6].reverse().join()
		logging("${device} : batteryVoltageHex byte flipped : ${batteryVoltageHex}", "trace")

		if (batteryVoltageHex == "FFFF") {
			// Occasionally a weird battery reading can be received. Ignore it.
			logging("${device} : batteryVoltageHex skipping anomolous reading : ${batteryVoltageHex}", "debug")
			return
		}

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
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "info")
			} else if (batteryPercentage > 30) {
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "info")
			} else {
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
			}

			sendEvent(name: "battery", value:batteryPercentage, unit: "%", isStateChange: false)
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %", isStateChange: false)
			sendEvent(name: "batteryState", value: "discharging", isStateChange: true)

		} else if (batteryVoltage < batteryVoltageScaleMin) {

			// Very low voltages indicate an exhausted battery which requires replacement.

			state.batteryOkay = false

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
		BigDecimal temperatureCelsius = zigbee.convertHexToInt(temperatureValue) / 16

		logging("${device} : temperatureCelsius sensor value : ${temperatureCelsius}", "trace")
		logging("${device} : Temperature : $temperatureCelsius°C", "info")
		sendEvent(name: "temperature", value: temperatureCelsius, unit: "C", isStateChange: false)
		sendEvent(name: "temperatureWithUnit", value: "${temperatureCelsius} °C", isStateChange: false)

	} else if (map.clusterId == "00F2") {

		// Tamper cluster.

		if (map.command == "00") {

			if (receivedData[0] == "02") {

				logging("${device} : Tamper : Detected", "warn")
				sendEvent(name: "tamper", value: "detected", isStateChange: true)

			} else {

				reportToDev(map)

			}

		} else if (map.command == "01") {

			if (receivedData[0] == "01") {

				logging("${device} : Tamper : Cleared", "info")
				sendEvent(name: "tamper", value: "clear", isStateChange: true)

			} else {

				reportToDev(map)

			}

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "00F3") {

		// Trigger cluster.

		// The push is always sent but the release is sent only when the button is held for a moment.
		// This means if you are using as an on/off you must expect an 'on' and then an 'off'.

		if (map.command == "00") {

			if (receivedData[1] == "02") {

				logging("${device} : Trigger : Button Released", "info")
				sendEvent(name: "released", value: 1, isStateChange: true)

			} else {

				reportToDev(map)

			}

		} else if (map.command == "01") {

			if (receivedData[1] == "01") {

				logging("${device} : Trigger : Button Pushed", "info")
				sendEvent(name: "pushed", value: 1, isStateChange: true)

			} else {

				reportToDev(map)

			}

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "00F6") {

		// Discovery cluster. 

		if (map.command == "FD") {

			// Ranging is our jam, Hubitat deals with joining on our behalf.

			def lqiRangingHex = "undefined"
			int lqiRanging = 0
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

			def versionInfoHex = receivedData[31..receivedData.size() - 1].join()

			StringBuilder str = new StringBuilder()
			for (int i = 0; i < versionInfoHex.length(); i+=2) {
				str.append((char) Integer.parseInt(versionInfoHex.substring(i, i + 2), 16))
			} 

			String versionInfo = str.toString()
			String[] versionInfoBlocks = versionInfo.split("\\s")
			int versionInfoBlockCount = versionInfoBlocks.size()
			String versionInfoDump = versionInfoBlocks[0..versionInfoBlockCount - 1].toString()

			logging("${device} : Version : ${versionInfoBlockCount} Blocks : ${versionInfoDump}", "info")

			String deviceManufacturer = "AlertMe"
			String deviceModel = ""
			String deviceFirmware = versionInfoBlocks[versionInfoBlockCount - 1]

			// Sometimes the model name contains spaces.
			if (versionInfoBlockCount == 2) {
				deviceModel = versionInfoBlocks[0]
			} else {
				deviceModel = versionInfoBlocks[0..versionInfoBlockCount - 2].join().toString()
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
