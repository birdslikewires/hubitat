/*
 * 
 *  AlertMe Smart Plug Driver v1.13 (6th August 2020)
 *	
 */


metadata {

	definition (name: "AlertMe Smart Plug", namespace: "AlertMe", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme_smartplug.groovy") {

		capability "Actuator"
		capability "Battery"
		capability "Initialize"
		capability "Outlet"
		capability "PowerMeter"
        capability "PresenceSensor"
		capability "Refresh"
		capability "Switch"
		capability "TemperatureMeasurement"

		//command "lockedMode"
		command "normalMode"
		command "rangingMode"
		//command "silentMode"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "string"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "mode", "string"
		attribute "powerWithUnit", "string"
		attribute "rssi", "string"
		attribute "stateMismatch", "boolean"
		attribute "supplyPresent", "boolean"
		attribute "temperatureWithUnit", "string"
		attribute "uptime", "string"
		attribute "uptimeReadable", "string"
		attribute "usage", "string"
		attribute "usageWithUnit", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F1,00EF,00EE", outClusters: "", manufacturer: "AlertMe.com", model: "Smart Plug", deviceJoinName: "AlertMe Smart Plug"
		
	}

}


preferences {
	
	input name: "batteryVoltageMinimum",type: "decimal",title: "Battery Minimum Voltage",description: "Low battery voltage (default: 3.6)",defaultValue: "3.6",range: "3.2..4.0"
	input name: "batteryVoltageMaximum",type: "decimal",title: "Battery Maximum Voltage",description: "Full battery voltage (default: 4.2)",defaultValue: "4.2",range: "3.6..4.8"
	input name: "infoLogging",type: "bool",title: "Enable logging",defaultValue: true
	input name: "debugLogging",type: "bool",title: "Enable debug logging",defaultValue: true
	input name: "traceLogging",type: "bool",title: "Enable trace logging",defaultValue: true
	
}


def installed() {
	// Runs after pairing.
	logging("${device} : Installing", "info")
}


def initialize() {
	configure()
}


def configure() {
	// Runs after installed() whenever a device is paired or rejoined.

	state.batteryInstalled = false
	state.operatingMode = "normal"
	state.rangingPulses = 0
	state.relayClosed = false
	state.uptimeReceived = 0

	device.updateSetting("infoLogging",[value:"true",type:"bool"])
	device.updateSetting("debugLogging",[value:"true",type:"bool"])
	device.updateSetting("traceLogging",[value:"true",type:"bool"])

	// Remove any scheduled events.
	unschedule()

	// Remove any old state variables.
	state.remove("uptime")
	state.remove("presentAt")

	// Bunch of zero or null values.
	sendEvent(name: "battery",value:0, unit: "%", isStateChange: false)
	sendEvent(name: "batteryState",value: "unknown", isStateChange: false)
	sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "batteryWithUnit", value: "unknown",isStateChange: false)
	sendEvent(name: "mode", value: "unknown",isStateChange: false)
	sendEvent(name: "power", value: 0, unit: "W", isStateChange: false)
	sendEvent(name: "powerWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "presence", value: "present")
	sendEvent(name: "rssi", value: "unknown")
	sendEvent(name: "stateMismatch",value: true, isStateChange: false)
	sendEvent(name: "supplyPresent",value: false, isStateChange: false)
	sendEvent(name: "switch", value: "unknown")
	sendEvent(name: "temperature", value: 0, unit: "C", isStateChange: false)
	sendEvent(name: "temperatureWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "uptime", value: 0, unit: "s", isStateChange: false)
	sendEvent(name: "uptimeReadable", value: "unknown")
	sendEvent(name: "usage", value: 0, unit: "Wh", isStateChange: false)
	sendEvent(name: "usageWithUnit", value: "unknown", isStateChange: false)

	// Schedule our ranging report.
	randomValue = Math.abs(new Random().nextInt() % 60)
	schedule("${randomValue} ${randomValue}/59 * * * ? *", rangeAndRefresh)		// At X seconds past the minute, every 59 minutes, starting at X minutes past the hour.

	// Schedule the presence check.
	randomValue = Math.abs(new Random().nextInt() % 60)
	schedule("${randomValue} 0/1 * * * ? *", checkPresence)						// At X seconds past the minute, every minute.

	// Set the operating mode and turn off advanced logging.
	rangingMode()
	runIn(6,normalMode)
	runIn(240,debugLogOff)
	runIn(120,traceLogOff)

	// All done.
	logging("${device} : Configured", "info")
	
}


def updated() {

	// Runs whenever preferences are saved.

	loggingStatus()
	refresh()

}


void loggingStatus() {

	log.info "${device} : Logging : ${infoLogging == true}"
	log.debug "${device} : Debug Logging : ${debugLogging == true}"
	log.trace "${device} : Trace Logging : ${traceLogging == true}"

}


void reportToDev(map) {

	String[] receivedData = map.data
	logging("${device} : UNKNOWN DATA! PLEASE REPORT THESE WARNINGS TO THE DEVELOPER", "warn")
	logging("${device} : Received clusterId ${map.clusterId} command ${map.command} with ${receivedData.length} values: ${receivedData}", "warn")
	logging("${device} : Splurge! ${map}", "warn")

}


void traceLogOff(){
	
	device.updateSetting("traceLogging",[value:"false",type:"bool"])
	log.trace "${device} : Trace Logging : Automatically Disabled"

}


void debugLogOff(){
	
	device.updateSetting("debugLogging",[value:"false",type:"bool"])
	log.debug "${device} : Debug Logging : Automatically Disabled"

}


def normalMode() {

	// This is the standard, quite chatty, running mode of the outlet.

	def someCommand = []
	someCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00F0 {11 00 FA 00 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(someCommand, hubitat.device.Protocol.ZIGBEE))
	refresh()
	state.operatingMode = "normal"
	sendEvent(name: "mode", value: "normal")
	logging("${device} : Mode : Normal", "info")

}


def rangingMode() {

	// Ranging mode double-flashes (good signal) or triple-flashes (poor signal) the indicator
	// while reporting RSSI values. It's also a handy means of identifying or pinging a device.

	// Don't set state.operatingMode here! Ranging is a temporary state only.

	def someCommand = []
	someCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00F0 {11 00 FA 01 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(someCommand, hubitat.device.Protocol.ZIGBEE))
	sendEvent(name: "mode", value: "ranging")
	logging("${device} : Mode : Ranging", "info")

	// Ranging will be disabled after a maximum of 30 pulses.
	state.rangingPulses = 0

}


def lockedMode() {

	// Locked mode is not as useful as it might first appear. This disables the local power button on
	// the outlet. However, this can be reset by rebooting the outlet by holding that same power
	// button for ten seconds. Or you could just turn off the supply, of course.

	// To complicate matters this mode cannot be disabled remotely, so far as I can tell.

	def someCommand = []
	someCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00F0 {11 00 FA 02 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(someCommand, hubitat.device.Protocol.ZIGBEE))
	refresh()
	state.operatingMode = "locked"
	sendEvent(name: "mode", value: "locked")
	logging("${device} : Mode : Locked", "info")

}


def silentMode() {

	// Turns off all reporting. Not hugely useful as we can control logging in other ways.

	def someCommand = []
	someCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00F0 {11 00 FA 03 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(someCommand, hubitat.device.Protocol.ZIGBEE))
	refresh()
	state.operatingMode = "silent"
	sendEvent(name: "mode", value: "silent")
	logging("${device} : Mode : Silent", "info")

}


def off() {

	// The off command is custom to AlertMe equipment, so has to be constructed.

	def offCommand = []
	offCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00EE {11 00 02 00 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(offCommand, hubitat.device.Protocol.ZIGBEE))

}


def on() {

	// The on command is custom to AlertMe equipment, so has to be constructed.

	def onCommand = []
	onCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00EE {11 00 02 01 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(onCommand, hubitat.device.Protocol.ZIGBEE))

}


void refresh() {

	// The Smart Plug becomes active after joining once it has received this status update request.
	// It also expects the Hub to check in with this occasionally, otherwise remote control is eventually dropped. 

	def stateRequest = []
	stateRequest.add("he raw ${device.deviceNetworkId} 0 2 0x00EE {11 00 01 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(stateRequest, hubitat.device.Protocol.ZIGBEE))
	logging("${device} : Refreshed", "info")

}


def rangeAndRefresh() {

	// This toggles ranging mode to check presence and update the device's RSSI value.

	rangingMode()
	runIn(3, "${state.operatingMode}Mode")

}


def checkPresence() {

	// Check how long ago the last uptime report was received.
	// These devices report uptime every 60 seconds. If no reports are seen after 240 seconds, we know something is wrong.

	long timeNow = new Date().time / 1000

	if (state.uptimeReceived > 0) {
		if (timeNow - state.uptimeReceived > 240) {
			sendEvent(name: "presence", value: "not present")
			logging("${device} : No recent uptime reports.", "warn")
			logging("${device} : checkPresence() : ${timeNow} - ${state.uptimeReceived} > 240", "trace")
		} else {
			sendEvent(name: "presence", value: "present")
			logging("${device} : Recent uptime report received.", "debug")
			logging("${device} : checkPresence() : ${timeNow} - ${state.uptimeReceived} < 240", "trace")
		}
	} else {
		logging("${device} : checkPresence() : Waiting for first uptime.", "debug")
	}

}


def parse(String description) {
	
	// Primary parse routine.

	def descriptionMap = zigbee.parseDescriptionAsMap(description)
	
	if (descriptionMap) {
	
		logging("${device} : Splurge!: ${descriptionMap}", "trace")
		outputValues(descriptionMap)

	} else {
		
		reportToDev(descriptionMap)

	}	

}


def outputValues(map) {

	String[] receivedData = map.data

	if (map.clusterId == "00EE") {

		// Switch actuation and power state messages.

		if (map.command == "80") {

			// Power States

			def powerStateHex = "undefined"
			powerStateHex = receivedData[0]

			// Power states are fun.
			//   00 00 - Cold mains power on with relay off (only occurs when battery dead or after reset)
			//   01 01 - Cold mains power on with relay on (only occurs when battery dead or after reset)
			//   02 00 - Mains power off and relay off [BATTERY OPERATION]
			//   03 01 - Mains power off and relay on [BATTERY OPERATION]
			//   04 00 - Mains power returns with relay off (only follows a 00 00)
			//   05 01 - Mains power returns with relay on (only follows a 01 01)
			//   06 00 - Mains power on and relay off (normal actuation)
			//   07 01 - Mains power on and relay on (normal actuation)

			if (powerStateHex == "02" || powerStateHex == "03") {

				// Supply failed.

				sendEvent(name: "batteryState", value: "discharging", isStateChange: true)
				sendEvent(name: "supplyPresent", value: false, isStateChange: true)

				// Whether this is a problem!

				if (powerStateHex == "02") {

					sendEvent(name: "stateMismatch", value: false, isStateChange: true)

				} else {

					sendEvent(name: "stateMismatch", value: true, isStateChange: true)

				}

			} else if (powerStateHex == "06" || powerStateHex == "07") {

				// Supply present.

				sendEvent(name: "stateMismatch", value: false, isStateChange: true)
				sendEvent(name: "supplyPresent", value: true, isStateChange: true)

			} else {

				// Supply returned!

				if (state.batteryInstalled) {
					sendEvent(name: "batteryState", value: "charging", isStateChange: true)
				}

				sendEvent(name: "stateMismatch", value: false, isStateChange: true)
				sendEvent(name: "supplyPresent", value: true, isStateChange: true)

			}

			// Relay Switch State

			def switchStateHex = "undefined"
			switchStateHex = receivedData[1]

			if (switchStateHex == "01") {

				state.relayClosed = true
				sendEvent(name: "switch", value: "on")
				logging("${device} : Switch : On", "info")

			} else {

				state.relayClosed = false
				sendEvent(name: "switch", value: "off")
				logging("${device} : Switch : Off", "info")

			}

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "00EF") {

		// Power and usage messages.

		if (map.command == "81") {

			// Power Reading

			def powerValueHex = "undefined"
			def int powerValue = 0

			// These power readings are so frequent that we only log them in debug or trace.
			powerValueHex = receivedData[0..1].reverse().join()
			logging("${device} : power byte flipped : ${powerValueHex}", "trace")
			powerValue = zigbee.convertHexToInt(powerValueHex)
			logging("${device} : power sensor reports : ${powerValue}", "debug")

			sendEvent(name: "power", value: powerValue, unit: "W", isStateChange: true)
			sendEvent(name: "powerWithUnit", value: "${powerValue} W", isStateChange: true)

		} else if (map.command == "82") {

			// Command 82 returns usage summary in watt-hours with an uptime counter.

			// Usage

			def usageValueHex = "undefined"
			def int usageValue = 0 

			usageValueHex = receivedData[0..3].reverse().join()
			logging("${device} : usage byte flipped : ${usageValueHex}", "trace")
			usageValue = zigbee.convertHexToInt(usageValueHex)
			logging("${device} : usage counter reports : ${usageValue}", "debug")

			usageValue = usageValue / 3600

			logging("${device} : Power Usage : ${usageValue} Wh", "info")

			sendEvent(name: "usage", value: usageValue, unit: "Wh", isStateChange: false)
			sendEvent(name: "usageWithUnit", value: "${usageValue} Wh", isStateChange: false)

			// Uptime

			def uptimeValueHex = "undefined"
			def int uptimeValue = 0

			uptimeValueHex = receivedData[4..8].reverse().join()
			logging("${device} : uptime byte flipped : ${uptimeValueHex}", "trace")
			uptimeValue = zigbee.convertHexToInt(uptimeValueHex)
			logging("${device} : uptime counter reports : ${uptimeValue}", "debug")

			def newDhmsUptime = []
			newDhmsUptime = secondsToDhms(uptimeValue)
			def String uptimeReadable = "${newDhmsUptime[3]}d ${newDhmsUptime[2]}h ${newDhmsUptime[1]}m"
			logging("${device} : Uptime : ${uptimeReadable}", "info")

			sendEvent(name: "uptime", value: uptimeValue, unit: "s", isStateChange: false)
			sendEvent(name: "uptimeReadable", value: uptimeReadable, isStateChange: false)

			// Presence

			long timeNow = new Date().time / 1000
			state.uptimeReceived = timeNow

		} else {

			// Unknown power usage data.
			reportToDev(map)

		}

	} else if (map.clusterId == "00F0") {

		// Device status, including battery and temperature data.

		// Report the battery voltage and calculated percentage.
		def batteryVoltageHex = "undefined"
		def BigDecimal batteryVoltage = 0
		batteryVoltageHex = receivedData[5..6].reverse().join()
		logging("${device} : batteryVoltageHex byte flipped : ${batteryVoltageHex}", "trace")
		batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex) / 1000
		sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V", isStateChange: false)
		sendEvent(name: "batteryVoltageWithUnit", value: "${batteryVoltage} V", isStateChange: false)

		// If the charge circuitry is reporting greater than 4.5 V then the battery is either missing or faulty.
		if (batteryVoltage >= 3.3 && batteryVoltage <= 4.4) {

			state.batteryInstalled = true

			def BigDecimal batteryPercentage = 0
			def BigDecimal minVoltage = batteryVoltageMinimum == null ? 3.6 : batteryVoltageMinimum
			def BigDecimal maxVoltage = batteryVoltageMaximum == null ? 4.2 : batteryVoltageMaximum

			if(maxVoltage - minVoltage > 0) {
				batteryPercentage = ((batteryVoltage - minVoltage) / (maxVoltage - minVoltage)) * 100.0
			} else {
				batteryPercentage = 100
			}

			batteryPercentage = batteryPercentage.setScale(0, BigDecimal.ROUND_HALF_UP)
			batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage
			batteryVoltage = batteryVoltage.setScale(3, BigDecimal.ROUND_HALF_UP)

			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "debug")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%", isStateChange: false)
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %", isStateChange: false)
			sendEvent(name: "batteryState", value: "charging", isStateChange: true)

		} else if (batteryVoltage < 3.3) {

			state.batteryInstalled = true

			batteryPercentage = 0

			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "debug")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%", isStateChange: false)
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %", isStateChange: false)
			sendEvent(name: "batteryState", value: "exhausted", isStateChange: true)

		} else {

			state.batteryInstalled = false

			batteryPercentage = 0

			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "debug")
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
		def BigDecimal correctionValue = (state.relayClosed) ? 0.6 : 0.75
		def BigDecimal temperatureCelsiusCorrected = Math.round(temperatureCelsius * correctionValue * 100) / 100
		logging("${device} : temperatureCelsiusCorrected : ${temperatureCelsiusCorrected} = ${temperatureCelsius} x ${correctionValue}", "trace")
		logging("${device} : Corrected Temperature : ${temperatureCelsiusCorrected} C", "debug")
		sendEvent(name: "temperature", value: temperatureCelsiusCorrected, unit: "C", isStateChange: false)
		sendEvent(name: "temperatureWithUnit", value: "${temperatureCelsiusCorrected} °C", isStateChange: false)

	} else if (map.clusterId == "00F2") {

		// Tamper status, not normally received from smart plugs.
		reportToDev(map)

	} else if (map.clusterId == "00F3") {

		// State change, not normally received from smart plugs.
		reportToDev(map)

	} else if (map.clusterId == "00F6") {

		// Ranging and join information. Hubitat deals with joining, we deal with ranging.

		if (map.command == "FD") {

			def rssiRangingHex = "undefined"
			def int rssiRanging = 0
			rssiRangingHex = receivedData[0]
			rssiRanging = zigbee.convertHexToInt(rssiRangingHex)
			sendEvent(name: "rssi", value: rssiRanging, isStateChange: false)
			logging("${device} : rssiRanging : ${rssiRanging}", "debug")

			if (receivedData[1] == "FF") {
				// This is an arbitrary ranging report, trigger a refresh for good measure.
				logging("${device} : arbitrary ranging report received, sending status update request : refresh() ", "trace")
				refresh()
			} else if (receivedData[1] == "77") {
				// This is ranging mode, which must be temporary. Make sure we come out of it.
				state.rangingPulses++
				if (state.rangingPulses > 30) {
					"${state.operatingMode}Mode"()
				}
			}

		} else {

			logging("${device} : Receiving a message on the join cluster. This Smart Plug probably wants us to ask how it's feeling.", "debug")
			logging("${device} : Received clusterId ${map.clusterId} command ${map.command} with ${receivedData.length} values: ${receivedData}", "trace")
			refresh()

		}

	} else if (map.clusterId == "8001" || map.clusterId == "8038") {

		// These clusters are sometimes received from the SPG100 and I have no idea why. Not all SPG100s send them.
		//   8001 arrives with 12 bytes of data
		//   8038 arrives with 27 bytes of data
		// This response is the equivalent of a nod and a smile when you've not heard someone properly.
		refresh()

	} else if (map.clusterId == "8032" ) {

		// These clusters are sometimes received when joining new devices to the mesh.
		//   8032 arrives with 80 bytes of data, probably routing and neighbour information.
		// We don't do anything with this, the mesh re-jigs itself and is apparently a known thing with AlertMe devices.
		logging("${device} : New join has triggered route table reshuffle. It may be worth checking all devices are still connected.", "warn")

	} else {

		// Not a clue what we've received.
		reportToDev(map)

	}

	return null

}


private String[] secondsToDhms(int timeToParse) {

	def dhms = []
	dhms.add(timeToParse % 60)
	timeToParse = timeToParse / 60
	dhms.add(timeToParse % 60)
	timeToParse = timeToParse / 60
	dhms.add(timeToParse % 24)
	timeToParse = timeToParse / 24
	dhms.add(timeToParse % 365)
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
