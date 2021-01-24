/*
 * 
 *  AlertMe Key Fob Driver v1.14 (24th January 2021)
 *	
 */


metadata {

	definition (name: "AlertMe Key Fob", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_keyfob.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "Initialize"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Refresh"
		capability "ReleasableButton"
		capability "SignalStrength"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "string"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "mode", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F4,00F1", outClusters: "", manufacturer: "AlertMe.com", model: "Keyfob Device", deviceJoinName: "AlertMe Key Fob"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


def installed() {
	// Runs after first pairing.
	logging("${device} : Paired!", "info")
}


def initialize() {

	// Set states to starting values and schedule a single refresh.
	// Runs on reboot, or can be triggered manually.

	// Reset states...

	state.batteryOkay = true
	state.lastAwayPress = 0
	state.lastAwayRelease = 0
	state.lastHomePress = 0
	state.lastHomeRelease = 0
	state.operatingMode = "normal"
	state.presenceUpdated = 0
	state.rangingPulses = 0

	// ...but don't arbitrarily reset the state of the device's main functions or tamper status.

	sendEvent(name: "battery",value:0, unit: "%", isStateChange: false)
	sendEvent(name: "batteryState",value: "discharging", isStateChange: false)
	sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "batteryWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "lqi", value: 0, isStateChange: false)
	sendEvent(name: "operation", value: "unknown", isStateChange: false)
	sendEvent(name: "presence", value: "not present", isStateChange: false)
	sendEvent(name: "temperature", value: 0, unit: "C", isStateChange: false)
	sendEvent(name: "temperatureWithUnit", value: "unknown", isStateChange: false)

	// Remove disused state variables from earlier versions.
	state.remove("batteryInstalled")
	state.remove("firmwareVersion")
	state.remove("lastPress")
	state.remove("lastRelease")
	state.remove("uptime")
	state.remove("uptimeReceived")
	state.remove("presentAt")
	state.remove("relayClosed")
	state.remove("rssi")
	state.remove("supplyPresent")

	// Remove unnecessary device details.
	removeDataValue("application")

	// Stagger our device init refreshes or we run the risk of DDoS attacking our hub on reboot!
	randomSixty = Math.abs(new Random().nextInt() % 60)
	runIn(randomSixty,refresh)

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
	device.updateSetting("debugLogging",[value:"false",type:"bool"])
	device.updateSetting("traceLogging",[value:"false",type:"bool"])

	// Schedule our ranging report.
	int checkEveryHours = 6																						// Request a ranging report and refresh every 6 hours or every 1 hour for outlets.						
	randomSixty = Math.abs(new Random().nextInt() % 60)
	randomTwentyFour = Math.abs(new Random().nextInt() % 24)
	schedule("${randomSixty} ${randomSixty} ${randomTwentyFour}/${checkEveryHours} * * ? *", rangeAndRefresh)	// At X seconds past X minute, every checkEveryHours hours, starting at Y hour.

	// Schedule the presence check.
	int checkEveryMinutes = 1																					// Check presence timestamp every 6 minutes or every 1 minute for key fobs.						
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)									// At X seconds past the minute, every checkEveryMinutes minutes.

	// Configuration complete.
	logging("${device} : Configured", "info")

	// Run a ranging report and then switch to normal operating mode.
	rangingMode()
	runIn(12,normalMode)
	
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
	sendEvent(name: "operation", value: "normal")
	logging("${device} : Operation : Normal", "info")

}


def rangingMode() {

	// Ranging mode double-flashes (good signal) or triple-flashes (poor signal) the indicator
	// while reporting LQI values. It's also a handy means of identifying or pinging a device.

	// Don't set state.operatingMode here! Ranging is a temporary state only.

	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F0 {11 00 FA 01 01} {0xC216}"])
	sendEvent(name: "operation", value: "ranging")
	logging("${device} : Operation : Ranging", "info")

	// Ranging will be disabled after a maximum of 30 pulses.
	state.rangingPulses = 0

}


def quietMode() {

	// Turns off all reporting except for a ranging message every 2 minutes.

	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F0 {11 00 FA 03 01} {0xC216}"])
	state.operatingMode = "quiet"

	// We don't receive any of these in quiet mode, so reset them.
	sendEvent(name: "battery",value:0, unit: "%", isStateChange: false)
	sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "batteryWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "operation", value: "quiet")

	logging("${device} : Operation : Quiet", "info")

	refresh()

}


void refresh() {

	logging("${device} : Refreshing", "info")
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F6 {11 00 FC 01} {0xC216}"])	   // version information request

}


def rangeAndRefresh() {

	// This toggles ranging mode to update the device's LQI value.

	int returnToModeSeconds = 6			// We use 3 seconds for outlets, 6 seconds for battery devices, which respond a little more slowly.

	rangingMode()
	runIn(returnToModeSeconds, "${state.operatingMode}Mode")

}


def updatePresence() {

	long millisNow = new Date().time
	state.presenceUpdated = millisNow

}


def checkPresence() {

	// Check how long ago the presence state was updated.

	// AlertMe devices check in with some sort of report at least every 2 minutes (every minute for outlets).

	// It would be suspicious if nothing was received after 4 minutes, but this check runs every 6 minutes
	// by default (every minute for key fobs) so we don't exaggerate a wayward transmission or two.

	presenceTimeoutMinutes = 4
	uptimeAllowanceMinutes = 5

	if (state.presenceUpdated > 0 && state.batteryOkay == true) {

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

				logging("${device} : Presence : Ignoring overdue presence reports for ${uptimeAllowanceMinutes} minutes. The hub was rebooted ${hubUptime} seconds ago.", "info")

			}

		} else {

			sendEvent(name: "presence", value: "present")
			logging("${device} : Presence : Last presence report ${secondsElapsed} seconds ago.", "debug")

		}

		logging("${device} : checkPresence() : ${millisNow} - ${state.presenceUpdated} = ${millisElapsed} (Threshold: ${presenceTimeoutMillis} ms)", "trace")

	} else if (state.presenceUpdated > 0 && state.batteryOkay == false) {

		sendEvent(name: "presence", value: "not present")
		logging("${device} : Presence : Battery too low! Reporting not present as this device will no longer be reliable.", "warn")

	} else {

		logging("${device} : Presence : Waiting for first presence report.", "warn")

	}

}


def parse(String description) {

	// Primary parse routine.

	logging("${device} : Parse : $description", "debug")

	state.batteryOkay == true ?	sendEvent(name: "presence", value: "present") : sendEvent(name: "presence", value: "not present")
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


def processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	// AlertMe values are always sent in a data element.
	String[] receivedData = map.data

	if (map.clusterId == "00F0") {

		// Device status cluster.

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
		sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V")
		sendEvent(name: "batteryVoltageWithUnit", value: "${batteryVoltage} V")

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

			sendEvent(name: "battery", value:batteryPercentage, unit: "%")
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
			sendEvent(name: "batteryState", value: "discharging")

		} else if (batteryVoltage < batteryVoltageScaleMin) {

			// Very low voltages indicate an exhausted battery which requires replacement.

			state.batteryOkay = false

			batteryPercentage = 0

			logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%")
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
			sendEvent(name: "batteryState", value: "exhausted")

		} else {

			// If the charge circuitry is reporting greater than 4.5 V then the battery is either missing or faulty.
			// THIS NEEDS TESTING ON THE EARLY POWER CLAMP

			state.batteryOkay = false

			batteryPercentage = 0

			logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%")
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
			sendEvent(name: "batteryState", value: "fault")

		}

	} else if (map.clusterId == "00F3") {

		// Trigger cluster.

		// On the Button a press is always sent when pushed, but the release is sent only when the button is held for a moment.
		// On the Keyfob both press and release are always sent, regardless of how long the button is held.
		// For some reason, key fobs are very 'bouncy' and often send more than one press or release per actuation, sometimes with a long delay.

		// IMPORTANT! Always force 'isStateChange: true' on sendEvent, otherwise pressing the same button more than once won't trigger anything!

		long buttonDebounceTimeoutMillis = 8000

		int buttonNumber
		String buttonName
		if (receivedData[0] == "00") {
			buttonNumber = 1
			buttonName = "Home"
		} else {
			buttonNumber = 2
			buttonName = "Away"
		}

		long millisNow = new Date().time
		long millisElapsedAwayPress = millisNow - state.lastAwayPress
		long millisElapsedAwayRelease = millisNow - state.lastAwayRelease
		long millisElapsedHomePress = millisNow - state.lastHomePress
		long millisElapsedHomeRelease = millisNow - state.lastHomeRelease

		if (map.command == "00") {
			// Release
			
			if (buttonNumber == 1) {
				// Home Button

				if (millisElapsedHomeRelease > buttonDebounceTimeoutMillis) {

					state.lastHomeRelease = millisNow
					logging("${device} : Trigger : Button ${buttonNumber} (${buttonName}) Released", "info")
					sendEvent(name: "released", value: buttonNumber, isStateChange: true)

				} else {

					logging("${device} : Debounced : Button ${buttonNumber} (${buttonName}) Release", "debug")

				}

			} else {
				// Away Button

				if (millisElapsedAwayRelease > buttonDebounceTimeoutMillis) {

					state.lastAwayRelease = millisNow
					logging("${device} : Trigger : Button ${buttonNumber} (${buttonName}) Released", "info")
					sendEvent(name: "released", value: buttonNumber, isStateChange: true)

				} else {

					logging("${device} : Debounced : Button ${buttonNumber} (${buttonName}) Release", "debug")

				}

			}

		} else if (map.command == "01") {
			// Press

			if (buttonNumber == 1) {
				// Home Button

				if (millisElapsedHomePress > buttonDebounceTimeoutMillis) {

					state.lastHomePress = millisNow
					logging("${device} : Trigger : Button ${buttonNumber} (${buttonName}) Pressed", "info")
					sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

				} else {

					logging("${device} : Debounced : Button ${buttonNumber} (${buttonName}) Press", "debug")

				}

			} else {
				// Away Button

				if (millisElapsedAwayPress > buttonDebounceTimeoutMillis) {

					state.lastAwayPress = millisNow
					logging("${device} : Trigger : Button ${buttonNumber} (${buttonName}) Pressed", "info")
					sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

				} else {

					logging("${device} : Debounced : Button ${buttonNumber} (${buttonName}) Press", "debug")

				}

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
			sendEvent(name: "lqi", value: lqiRanging)
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

			// Device version response.

			def versionInfoHex = receivedData[31..receivedData.size() - 1].join()

			StringBuilder str = new StringBuilder()
			for (int i = 0; i < versionInfoHex.length(); i+=2) {
				str.append((char) Integer.parseInt(versionInfoHex.substring(i, i + 2), 16))
			} 

			String versionInfo = str.toString()
			String[] versionInfoBlocks = versionInfo.split("\\s")
			int versionInfoBlockCount = versionInfoBlocks.size()
			String versionInfoDump = versionInfoBlocks[0..versionInfoBlockCount - 1].toString()

			logging("${device} : device version received in ${versionInfoBlockCount} blocks : ${versionInfoDump}", "debug")

			String deviceManufacturer = "AlertMe"
			String deviceModel = ""
			String deviceFirmware = versionInfoBlocks[versionInfoBlockCount - 1]

			// Sometimes the model name contains spaces.
			if (versionInfoBlockCount == 2) {
				deviceModel = versionInfoBlocks[0]
			} else {
				deviceModel = versionInfoBlocks[0..versionInfoBlockCount - 2].join(' ').toString()
			}

			logging("${device} : Device : ${deviceModel}", "info")
			logging("${device} : Firmware : ${deviceFirmware}", "info")

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
