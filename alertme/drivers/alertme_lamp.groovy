/*
 * 
 *  AlertMe Lamp Driver v1.24 (27th September 2022)
 *	
 */


#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 2		// The real reporting interval of the device.
@Field int checkEveryMinutes = 6			// How often we should check for presence.
@Field int rangeEveryHours = 6				// How often we run a ranging report.


metadata {

	definition (name: "AlertMe Lamp", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_lamp.groovy") {

		capability "Actuator"
		capability "Battery"
		capability "ColorControl"
		capability "ColorMode"
		capability "Configuration"
		capability "Light"
		capability "PresenceSensor"
		capability "Refresh"
		capability "SignalStrength"
		capability "Switch"
		capability "SwitchLevel"
		capability "VoltageMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		command "lampSeqRGB"
		command "lampSeqSleepy"
		command "pause"

		attribute "batteryState", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F5", outClusters: "", manufacturer: "AlertMe.com", model: "Beacon", deviceJoinName: "AlertMe Lamp"
		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F5", outClusters: "", manufacturer: "AlertMe.com", model: "Lamp Device", deviceJoinName: "AlertMe Lamp"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F6 {11 00 FC 01} {0xC216}"])	   // version information request
	
}


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.alertme

	device.name = "AlertMe Lamp"

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


void off() {

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


void on() {

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


void pause() {

	def cmds = new ArrayList<String>()
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 04 00 01} {0xC216}")		// disable sequencing
	sendZigbeeCommands(cmds)

	logging("${device} : Lamp : Paused", "info")

}


void setColor(Map colormap) {

	if (colormap.containsValue("NaN")) {

		logging("${device} : Set Colour : Invalid value, it's probably that you've not moved the hue slider.", "info")


	} else {

		state.lastHue = colormap.hue
		state.lastSaturation = colormap.saturation
		state.lastLevel = colormap.level

		logging("${device} : Set Colour : Saved hue (${colormap.hue}), saturation (${colormap.saturation}) and level (${colormap.level}).", "info")

	}

}


void setHue(BigDecimal hue) {

	state.lastHue = hue <= 100 ? hue : 100

	logging("${device} : Set Hue : Saved hue (${hueSafe}).", "info")

}

void setSaturation(BigDecimal sat) {

	state.lastSaturation = sat <= 100 ? sat : 100

	logging("${device} : Set Saturation : Saved saturation (${satSafe}).", "info")

}


void setLevel(BigDecimal level, BigDecimal duration) {

	state.lastLevel = level <= 100 ? level : 100
	state.lastDuration = duration < 13107 ? duration : 13106

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Saved level (${state.lastLevel}%) reached over ${state.lastDuration} second${pluralisor}.", "info")

}


// A couple of presets to illustrate. :)


void lampSeqRGB() {

	def cmds = new ArrayList<String>()
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 1A 00 08 00 FF 00 00 00 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 1A 00 08 00 00 FF 00 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 1A 00 08 00 00 00 FF 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 05 FD 01} {0xC216}")
	sendZigbeeCommands(cmds)

	logging("${device} : Lamp : RGB Sequence", "info")

}


void lampSeqSleepy() {

	def cmds = new ArrayList<String>()
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 14 00 06 00 06 0A 0A 00 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 01 0F 00 01 00 58 7F 7F 01 01} {0xC216}")
	cmds.add("he raw ${device.deviceNetworkId} 0 2 0x00F5 {11 00 05 FD 01} {0xC216}")
	sendZigbeeCommands(cmds)

	logging("${device} : Lamp : Sleepy", "info")

}

