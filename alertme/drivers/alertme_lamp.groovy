/*
 * 
 *  AlertMe Lamp Driver v1.16 (9th January 2021)
 *	
 */


metadata {

	definition (name: "AlertMe Lamp", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_lamp.groovy") {

		capability "Actuator"
		capability "Battery"
		capability "ColorControl"
		capability "ColorMode"
		capability "Configuration"
		capability "Initialize"
		capability "Light"
		capability "PresenceSensor"
		capability "Refresh"
		capability "SignalStrength"
		capability "Switch"
		capability "SwitchLevel"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		command "lampSeqRGB"
		command "lampSeqSleepy"
		command "pause"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "string"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "mode", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F5", outClusters: "", manufacturer: "AlertMe.com", model: "Beacon", deviceJoinName: "AlertMe Lamp"
		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F5", outClusters: "", manufacturer: "AlertMe.com", model: "Lamp Device", deviceJoinName: "AlertMe Lamp"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}




// I can't believe this finally works! Incomplete, but working!
//
// The lamp operates with sequences, up to 10 steps in a sequence.
// The lamp must be put into 'on' mode (done during configuration) and then a sequence uploaded and run.
// Single colours are a one-step sequence.
//
// Lamp Cluster ID = 0x00F5
//
// {11 00 01 01 00 01 00 FF FF FF 00 01}
//  AA AA BB CC CC DD DD EE FF GG HH II
//
//  AA - Unknown preamble, same for all AlertMe devices.
//  BB - Cluster command; add state (01).
//  CC - Cluster control; transition time in multiples of 0.2s, 0x0001 through 0xFFFE (little endian in command)
//  DD - Cluster control; dwell time in multiples of 0.2s, 0x0001 through 0xFFFE (little endian in command)
//  EE - Red level as 8-bit hex value
//  FF - Green level as 8-bit hex value
//  GG - Blue level as 8-bit hex value
//  HH - Sequence behaviour; clear all previous (00), append (01) or append with confirmation (02). New sequences must commence with clear.
//  II - Unknown postamble, also the same for all AlertMe devices.
//
// {11 00 04 01 01}
//  AA AA BB CC DD
//
//  AA - Unknown preamble, same for all AlertMe devices.
//  BB - Cluster command; set operating mode (04).
//  CC - Cluster control; halt sequence (00), enable sequence (01) or single state (02).
//  DD - Unknown postamble, also the same for all AlertMe devices.
//
// {11 00 05 FD 01}
//  AA AA BB CC DD
//
//  AA - Unknown preamble, same for all AlertMe devices.
//  BB - Cluster command; set play mode (05).
//  CC - Cluster control; loop count, values between (00) and (FD) are valid, where (00) is load and hold and (FD) is loop indefinitely.
//  DD - Unknown postamble, also the same for all AlertMe devices.




def lampSeqRGB() {

	def cmds = new ArrayList<String>()
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 1A 00 08 00 FF 00 00 00 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 1A 00 08 00 00 FF 00 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 1A 00 08 00 00 00 FF 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 05 FD 01} {0xC216}")
	sendZigbeeCommands(cmds)

	logging("${device} : Lamp : RGB Sequence", "info")

}


def lampSeqSleepy() {

	def cmds = new ArrayList<String>()
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 14 00 06 00 06 0A 0A 00 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 0F 00 01 00 58 7F 7F 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 05 FD 01} {0xC216}")
	sendZigbeeCommands(cmds)

	logging("${device} : Lamp : Sleepy", "info")

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
	state.lastDuration = 0
	state.lastHue = 0
	state.lastSaturation = 0
	state.lastLevel = 0
	state.operatingMode = "normal"
	state.presenceUpdated = 0
	state.rangingPulses = 0

	// ...but don't arbitrarily reset the state of the device's main functions or tamper status.

	sendEvent(name: "battery", value:0, unit: "%", isStateChange: false)
	sendEvent(name: "batteryState", value: "discharging", isStateChange: false)
	sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "batteryWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "colorMode", value: "RGB", isStateChange: false)
	sendEvent(name: "lqi", value: 0, isStateChange: false)
	sendEvent(name: "operation", value: "unknown", isStateChange: false)
	sendEvent(name: "presence", value: "not present", isStateChange: false)

	// Remove disused state variables from earlier versions.
	state.remove("batteryInstalled")
	state.remove("firmwareVersion")	
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

	// Lamp test cycles red, green, blue, white and off.
	def cmds = new ArrayList<String>()
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 04 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 01 00 01 00 FF 00 00 00 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 01 00 01 00 00 FF 00 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 01 00 01 00 00 00 FF 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 01 00 01 00 FF FF FF 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 01 00 01 00 00 00 00 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 05 01 01} {0xC216}")
	sendZigbeeCommands(cmds)

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
	int checkEveryMinutes = 6																					// Check presence timestamp every 6 minutes or every 1 minute for key fobs.						
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
	logging("${device} : Mode : Normal", "info")

}


def rangingMode() {

	// Ranging mode double-flashes (good signal) or triple-flashes (poor signal) the indicator
	// while reporting LQI values. It's also a handy means of identifying or pinging a device.

	// Don't set state.operatingMode here! Ranging is a temporary state only.

	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F0 {11 00 FA 01 01} {0xC216}"])
	sendEvent(name: "operation", value: "ranging")
	logging("${device} : Mode : Ranging", "info")

	// Ranging will be disabled after a maximum of 30 pulses.
	state.rangingPulses = 0

}


def quietMode() {

	// Turns off all reporting except for a ranging message every 2 minutes.

	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F0 {11 00 FA 03 01} {0xC216}"])
	state.operatingMode = "quiet"

	// We don't receive any of these in quiet mode, so reset them.
	sendEvent(name: "battery", value:0, unit: "%", isStateChange: false)
	sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "batteryWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "operation", value: "quiet")

	logging("${device} : Mode : Quiet", "info")

	refresh()

}


def off() {

	// Convert seconds to multiple of 0.2s, or use 0.2s if duration is zero.
	BigInteger dur = state.lastDuration * 5
	String[] durHex = dur > 0 ? dur.toString(16).toUpperCase().padLeft(4,'0') : [0,0,0,0]

	def cmds = new ArrayList<String>()
	// clear sequence, set RGB to 00 00 00, dwell indefinitely, transition as configured, add to sequence
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 ${durHex[2..3].join()} ${durHex[0..1].join()} 01 00 00 00 00 00 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 04 02 01} {0xC216}")		// single state play mode
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 05 FD 01} {0xC216}")		// indefintely loop and play
	sendZigbeeCommands(cmds)

	sendEvent(name: "switch", value: "off")
	logging("${device} : Lamp : Off", "info")

}


def on() {

	// The RGB LEDs in these lamps are not calibrated so you will get colour variations and potentially some flickering at certain values.

	def hsl = [state.lastHue,state.lastSaturation,state.lastLevel]
	def rgb = hubitat.helper.ColorUtils.hsvToRGB(hsl)

	// Limit maximum values for code safety.
	BigInteger red = rgb[0] <= 255 ? rgb[0] : 255
	BigInteger grn = rgb[1] <= 255 ? rgb[1] : 255
	BigInteger blu = rgb[2] <= 255 ? rgb[2] : 255

	// No settings? No problem!
	if (red + grn + blu == 0) {
		(red, grn, blu) = [255,255,255]
	}

	// Convert to RGB to hex values.
	String redHex = red.toString(16).toUpperCase().padLeft(2,'0')
	String grnHex = grn.toString(16).toUpperCase().padLeft(2,'0')
	String bluHex = blu.toString(16).toUpperCase().padLeft(2,'0')


	// Convert seconds to multiple of 0.2s, or use 0.2s if duration is zero.
	BigDecimal dur = state.lastDuration / 0.2
	String[] durHex = dur > 0 ? dur.toBigInteger().toString(16).toUpperCase().padLeft(4,'0') : [0,0,0,0]

	def cmds = new ArrayList<String>()
	// clear sequence, set RGB, dwell indefinitely, transition as configured, add to sequence
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 ${durHex[2..3].join()} ${durHex[0..1].join()} 01 00 ${redHex} ${grnHex} ${bluHex} 00 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 04 02 01} {0xC216}")		// single state play mode
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 05 FD 01} {0xC216}")		// indefintely loop and play
	sendZigbeeCommands(cmds)

	logging("${device} : on : Stored HSL values convert to RGB as ${rgb} (${redHex}, ${grnHex}, ${bluHex})", "debug")

	sendEvent(name: "switch", value: "on")
	logging("${device} : Lamp : On", "info")

}


def pause() {

	def cmds = new ArrayList<String>()
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 04 00 01} {0xC216}")		// disable sequencing
	sendZigbeeCommands(cmds)

	logging("${device} : Lamp : Paused", "info")

}


def setColor(Map colormap) {

	if (colormap.containsValue("NaN")) {

		logging("${device} : Set Colour : Invalid value, it's probably that you've not moved the hue slider.", "info")


	} else {

		state.lastHue = colormap.hue
		state.lastSaturation = colormap.saturation
		state.lastLevel = colormap.level

		logging("${device} : Set Colour : Saved hue (${colormap.hue}), saturation (${colormap.saturation}) and level (${colormap.level}).", "info")

	}

}


def setHue(BigDecimal hue) {

	state.lastHue = hue <= 100 ? hue : 100

	logging("${device} : Set Hue : Saved hue (${hueSafe}).", "info")

}

def setSaturation(BigDecimal sat) {

	state.lastSaturation = sat <= 100 ? sat : 100

	logging("${device} : Set Saturation : Saved saturation (${satSafe}).", "info")

}


def setLevel(BigDecimal level, BigDecimal duration) {

	state.lastLevel = level <= 100 ? level : 100
	state.lastDuration = duration < 13107 ? duration : 13106

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Saved level (${state.lastLevel}%) reached over ${state.lastDuration} second${pluralisor}.", "info")

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

	long millisNow = new Date().time

	presenceTimeoutMinutes = 4

	if (state.presenceUpdated > 0) {

		long millisElapsed = millisNow - state.presenceUpdated
		long presenceTimeoutMillis = presenceTimeoutMinutes * 60000
		BigDecimal secondsElapsed = millisElapsed / 1000

		if (millisElapsed > presenceTimeoutMillis) {

			sendEvent(name: "battery", value:0, unit: "%", isStateChange: false)
			sendEvent(name: "batteryState", value: "discharging", isStateChange: false)
			sendEvent(name: "batteryVoltage", value: 0, unit: "V", isStateChange: false)
			sendEvent(name: "batteryVoltageWithUnit", value: "unknown", isStateChange: false)
			sendEvent(name: "lqi", value: 0)
			sendEvent(name: "presence", value: "not present")
			logging("${device} : Not Present : Last presence report ${secondsElapsed} seconds ago.", "warn")

		} else {

			sendEvent(name: "presence", value: "present")
			logging("${device} : Present : Last presence report ${secondsElapsed} seconds ago.", "debug")

		}

		logging("${device} : checkPresence() : ${millisNow} - ${state.presenceUpdated} = ${millisElapsed} (Threshold: ${presenceTimeoutMillis})", "trace")

	} else {

		logging("${device} : Waiting for first presence report.", "warn")

	}

}


def parse(String description) {

	// Primary parse routine.

	logging("${device} : Parse : $description", "debug")

	sendEvent(name: "presence", value: "present")
	updatePresence()

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Splurge! : ${description}", "warn")

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

		// All firmware I've seen on the Lamp fudges the voltage to ~3 V, so multiply it up closer to reality.
		batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex) / 1000 * 1.40
		logging("${device} : batteryVoltage sensor value : ${batteryVoltage}", "debug")

		batteryVoltage = batteryVoltage.setScale(3, BigDecimal.ROUND_HALF_UP)

		logging("${device} : batteryVoltage : ${batteryVoltage}", "debug")
		sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V")
		sendEvent(name: "batteryVoltageWithUnit", value: "${batteryVoltage} V")

		BigDecimal batteryPercentage = 0
		BigDecimal batteryVoltageScaleMin = 3.5
		BigDecimal batteryVoltageScaleMax = 4.1

		if (batteryVoltage >= batteryVoltageScaleMin && batteryVoltage <= 4.4) {

			// A good three-cell 3.6 V NiMH battery will sit between 4.10 V and 4.25 V.

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

			if (batteryVoltage > batteryVoltageScaleMax) {
				!state.supplyPresent ?: sendEvent(name: "batteryState", value: "charged")
			} else {
				!state.supplyPresent ?: sendEvent(name: "batteryState", value: "charging")
			}

		} else if (batteryVoltage < batteryVoltageScaleMin) {

			// Very low voltages indicate an exhausted battery which requires replacement.

			state.batteryOkay = false

			batteryPercentage = 0

			logging("${device} : Battery : Exhausted battery.", "warn")
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%")
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
			sendEvent(name: "batteryState", value: "exhausted")

		} else {

			// If the charge circuitry is reporting greater than 4.5 V then the battery is either missing or faulty.
			// THIS NEEDS TESTING ON THE EARLY POWER CLAMP

			state.batteryOkay = false

			batteryPercentage = 0

			logging("${device} : Battery : Exhausted battery.", "warn")
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
			sendEvent(name: "battery", value:batteryPercentage, unit: "%")
			sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
			sendEvent(name: "batteryState", value: "fault")

		}

	} else if (map.clusterId == "00F2") {

		// Tamper cluster.

		if (map.command == "00") {

			if (receivedData[0] == "02") {

				logging("${device} : Tamper : Detected", "warn")
				sendEvent(name: "tamper", value: "detected")

			} else {

				reportToDev(map)

			}

		} else if (map.command == "01") {

			if (receivedData[0] == "01") {

				logging("${device} : Tamper : Cleared", "info")
				sendEvent(name: "tamper", value: "clear")

			} else {

				reportToDev(map)

			}

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "00F3") {

		// Trigger cluster.

		// On the Button a push is always sent on press, but the release is sent only when the button is held for a moment.
		// On the Keyfob both push and release are always sent, regardless of how long the button is held.

		// IMPORTANT! Always force 'isStateChange: true' on sendEvent, otherwise pressing the same button more than once won't trigger anything!

		int buttonNumber = 0

		if (receivedData[0] == "00") {
			buttonNumber = 1
		} else {
			buttonNumber = 2
		}

		if (map.command == "00") {

			logging("${device} : Trigger : Button ${buttonNumber} Released", "info")
			sendEvent(name: "released", value: buttonNumber, isStateChange: true)

		} else if (map.command == "01") {

			logging("${device} : Trigger : Button ${buttonNumber} Pressed", "info")
			sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

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
