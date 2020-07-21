/*
 * 
 *  AlertMe Smart Plug Driver v1.04 (21st July 2020)
 *	
 */


metadata {

	definition (name: "AlertMe Smart Plug", namespace: "AlertMe", author: "Andrew Davison") {

		capability "Actuator"
		capability "Battery"
		capability "Initialize"
		capability "Outlet"
		capability "Power Meter"
		capability "Refresh"
		capability "Switch"
		capability "Temperature Measurement"

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
		attribute "usage", "string"
		attribute "usageWithUnit", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F1,00EF,00EE", outClusters: "", manufacturer: "AlertMe.com", model: "Smart Plug", deviceJoinName: "AlertMe Smart Plug"
		
	}

}


preferences {
	
	input name: "vMinSetting",type: "decimal",title: "Battery Minimum Voltage",description: "Low battery voltage (default: 3.0)",defaultValue: "3.0",range: "2.7..3.3"
	input name: "vMaxSetting",type: "decimal",title: "Battery Maximum Voltage",description: "Full battery voltage (default: 3.6)",defaultValue: "3.6",range: "3.3..3.9"
	input name: "infoLogging",type: "bool",title: "Enable logging",defaultValue: true
	input name: "debugLogging",type: "bool",title: "Enable debug logging",defaultValue: false
	
}


def initialize() {
	
	state.batteryInstalled = false
	state.operatingMode = "normal"
	
	// Remove any scheduled events.
	unschedule()

	// Bunch of zero or null values.
	sendEvent(name:"battery",value:0, unit: "%", isStateChange: false)
	sendEvent(name:"batteryState",value: "unknown", isStateChange: false)
	sendEvent(name:"batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name:"batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name:"batteryWithUnit", value: "unknown",isStateChange: false)
	sendEvent(name:"mode", value: "unknown",isStateChange: false)
	sendEvent(name:"power", value: 0, unit: "W", isStateChange: false)
	sendEvent(name:"powerWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name:"rssi", value: "unknown")
	sendEvent(name:"stateMismatch",value: true, isStateChange: false)
	sendEvent(name:"supplyPresent",value: false, isStateChange: false)
	sendEvent(name:"switch", value: "unknown")
	sendEvent(name:"temperature", value: 0, unit: "C", isStateChange: false)
	sendEvent(name:"temperatureWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name:"uptime", value: 0, unit: "s", isStateChange: false)
	sendEvent(name:"usage", value: 0, unit: "Wh", isStateChange: false)
	sendEvent(name:"usageWithUnit", value: "unknown", isStateChange: false)

	// Schedule our refresh check-in and turn off the logs.
	randomMinute = Math.abs(new Random().nextInt() % 60)
	schedule("0 ${randomMinute} 0/1 1/1 * ? *", rangeAndRefresh)
	runIn(600,debugLogOff)
	runIn(600,infoLogOff)

	// Report our logging status.
	logging("Info logging is: ${infoLogging == true}",100)
	logging("Debug logging is: ${debugLogging == true}",1)

	// Set the operating mode.
	rangingMode()
	runIn(6,normalMode)

	// All done.
	logging("Initialised!",100)
	
}


void debugLogOff(){
	
	logging("Debug logging disabled.",101)
	device.updateSetting("debugLogging",[value:"false",type:"bool"])

}


void infoLogOff(){
	
	logging("Logging disabled.",101)
	device.updateSetting("infoLogging",[value:"false",type:"bool"])

}


def normalMode() {

	// This is the standard, quite chatty, running mode of the outlet.

	def someCommand = []
	someCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00F0 {11 00 FA 00 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(someCommand, hubitat.device.Protocol.ZIGBEE))
	refresh()
	state.operatingMode = "normal"
	sendEvent(name: "mode", value: "normal")
	logging("${device} : Mode : Normal",100)

}


def rangingMode() {

	// Ranging mode double-flashes (good signal) or triple-flashes (poor signal) the indicator
	// while reporting RSSI values. It's also a handy means of identifying a device.

	// Don't set state.operatingMode here! Ranging is a temporary state only.

	def someCommand = []
	someCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00F0 {11 00 FA 01 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(someCommand, hubitat.device.Protocol.ZIGBEE))
	sendEvent(name: "mode", value: "ranging")
	logging("${device} : Mode : Ranging",100)
	runIn(60,normalMode)
	runIn(90,normalMode)  // It's kind of important we get out of this mode, so here's the safety.

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
	logging("${device} : Mode : Locked",100)

}


def silentMode() {

	// Turns off all reporting. Not hugely useful as we can control logging in other ways.

	def someCommand = []
	someCommand.add("he raw ${device.deviceNetworkId} 0 2 0x00F0 {11 00 FA 03 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(someCommand, hubitat.device.Protocol.ZIGBEE))
	refresh()
	state.operatingMode = "silent"
	sendEvent(name: "mode", value: "silent")
	logging("${device} : Mode : Silent",100)

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


def refresh() {

	// The Smart Plug becomes active after joining once it has received this status update request.
	// It also expects the Hub to check in with this occasionally, otherwise remote control is dropped. 

	def stateRequest = []
	stateRequest.add("he raw ${device.deviceNetworkId} 0 2 0x00EE {11 00 01 01} {0xC216}")
	sendHubCommand(new hubitat.device.HubMultiAction(stateRequest, hubitat.device.Protocol.ZIGBEE))
	logging("${device} : Refreshed",100)

}


def rangeAndRefresh() {

	// This is a ranging report and refresh call.
	rangingMode()
	runIn(3,"${state.operatingMode}Mode")

}


def parse(String description) {
		
	def descriptionMap = zigbee.parseDescriptionAsMap(description)
	
	if (descriptionMap) {
	
		logging("splurge: ${descriptionMap}",1)
		outputValues(descriptionMap)

	} else {
		
		logging("PARSE FAILED: $description",101)

	}	

}


def outputValues(map) {

	String[] receivedData = map.data

	if (map.clusterId == "00EE") {

		// Cluster 00EE deals with switch actuation results and power states.

		if (map.command == "80") {

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

				sendEvent(name: "batteryState", value: "discharging", isStateChange: true)
				sendEvent(name: "supplyPresent", value: false, isStateChange: true)

				if (powerStateHex == "02") {

					sendEvent(name: "stateMismatch", value: false, isStateChange: true)

				} else {

					sendEvent(name: "stateMismatch", value: true, isStateChange: true)

				}

			} else {

				if (state.batteryInstalled) {

					sendEvent(name: "batteryState", value: "charging", isStateChange: true)

				} else {

					sendEvent(name: "batteryState", value: "unknown", isStateChange: true)

				}

				sendEvent(name: "stateMismatch", value: false, isStateChange: true)
				sendEvent(name: "supplyPresent", value: true, isStateChange: true)

			}

			def switchStateHex = "undefined"
			switchStateHex = receivedData[1]

			if (switchStateHex == "01") {

				sendEvent(name:"switch", value: "on")
				logging("${device} : Switch : On",100)

			} else {

				sendEvent(name:"switch", value: "off")
				logging("${device} : Switch : Off",100)

			}

		} else {

			logging("Unknown switch information! Please report this to the developer.",101)
			logging("Received clusterId ${map.clusterId} command ${map.command} with ${receivedData.length} values: ${receivedData}",101)
			logging("Splurge! ${map}",101)

		}

	} else if (map.clusterId == "00EF") {

		// Cluster 00EF deals with power usage information.

		if (map.command == "81") {

			// Command 81 returns immediate power readings.

			def powerValueHex = "undefined"
			def int powerValue = 0

			powerValueHex = receivedData[0..1].reverse().join()
			logging("${device} : power byte flipped : ${powerValueHex}",1)
			powerValue = zigbee.convertHexToInt(powerValueHex)
			logging("${device} : power sensor reports : ${powerValue}",1)

			logging("${device} : Power Reading : ${powerValue} W",100)

			sendEvent(name: "power", value: powerValue, unit: "W", isStateChange: true)
			sendEvent(name: "powerWithUnit", value: "${powerValue} W", isStateChange: true)

		} else if (map.command == "82") {

			// Command 82 returns usage summary in watt-hours with an uptime counter.

			def usageValueHex = "undefined"
			def int usageValue = 0 

			usageValueHex = receivedData[0..3].reverse().join()
			logging("${device} : usage byte flipped : ${usageValueHex}",1)
			usageValue = zigbee.convertHexToInt(usageValueHex)
			logging("${device} : usage counter reports : ${usageValue}",1)

			usageValue = usageValue / 3600

			logging("${device} : Power Usage : ${usageValue} Wh",100)

			sendEvent(name:"usage", value: usageValue, unit: "Wh", isStateChange: false)
			sendEvent(name:"usageWithUnit", value: "${usageValue} Wh", isStateChange: false)

			def uptimeValueHex = "undefined"
			def int uptimeValue = 0

			uptimeValueHex = receivedData[4..8].reverse().join()
			logging("${device} : uptime byte flipped : ${uptimeValueHex}",1)
			uptimeValue = zigbee.convertHexToInt(uptimeValueHex)
			logging("${device} : uptime counter reports : ${uptimeValue}",1)

			logging("${device} : Uptime : ${uptimeValue} s",100)

			sendEvent(name:"uptime", value: uptimeValue, unit: "s", isStateChange: false)

		} else {

			logging("Unknown power usage information! Please report this to the developer.",101)
			logging("Received clusterId ${map.clusterId} command ${map.command} with ${receivedData.length} values: ${receivedData}",101)
			logging("Splurge! ${map}",101)

		}

	} else if (map.clusterId == "00F0") {

		// Cluster 00F0 deals with device status, including battery and temperature data.

		// Report the battery voltage and calculated percentage.
		def batteryVoltageHex = "undefined"
		def float batteryVoltage = 0
		batteryVoltageHex = receivedData[5..6].reverse().join()
		logging("${device} : batteryVoltageHex byte flipped : ${batteryVoltageHex}",1)
		batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex) / 1000
		sendEvent(name:"batteryVoltage", value: batteryVoltage, unit: "V", isStateChange: false)
		sendEvent(name:"batteryVoltageWithUnit", value: "${batteryVoltage} V", isStateChange: false)

		// If the charge circuitry is reporting greater than 4.5 V then the battery is either missing or faulty.
		if (batteryVoltage <= 4.5) {
			state.batteryInstalled = true
			parseAndSendBatteryStatus(batteryVoltage)
		} else {
			state.batteryInstalled = false
			sendEvent(name:"battery", value:0, unit: "%", isStateChange: false)
			sendEvent(name:"batteryWithUnit", value:"0 %", isStateChange: false)
		}

		// Report the temperature in celsius.
		def temperatureValue = "undefined"
		temperatureValue = receivedData[7..8].reverse().join()
		logging("${device} : temperatureValue byte flipped : ${temperatureValue}",1)
		temperatureValue = zigbee.convertHexToInt(temperatureValue) / 16
		logging("${device} : Temperature : ${temperatureValue} C",100)
		sendEvent(name:"temperature", value: temperatureValue, unit: "C", isStateChange: false)
		sendEvent(name:"temperatureWithUnit", value: "${temperatureValue} °C", unit: "C", isStateChange: false)

	} else if (map.clusterId == "00F3") {

		logging("Received tamper button status. Smart Plugs don't normally send this, please report to developer.",101)
		logging("Received clusterId ${map.clusterId} command ${map.command} with ${receivedData.length} values: ${receivedData}",101)

	} else if (map.clusterId == "00F6") {

		if (map.command == "FD") {

			def rssiRangingHex = "undefined"
			def int rssiRanging = 0
			rssiRangingHex = receivedData[0]
			rssiRanging = zigbee.convertHexToInt(rssiRangingHex)
			sendEvent(name:"rssi", value: rssiRanging, isStateChange: false)
			logging("${device} : rssiRanging : ${rssiRanging}",1)

			if (receivedData[1] == "FF") {
				// If this is a general ranging report, trigger a refresh for good measure.
				refresh()
			} //else if (receivedData[1] == "77") {
				// You are in ranging mode!
				// This is to record that 77 is ranging mode.
				// There might be something cool you could do here, but nothing springs to mind right now.
			//}

		} else {

			logging("Receiving a message on the join cluster. This Smart Plug probably wants us to ask how it's feeling.",101)
			logging("Received clusterId ${map.clusterId} command ${map.command} with ${receivedData.length} values: ${receivedData}",101)
			refresh()

		}

	} else {

		logging("Unknown cluster received! Please report this to the developer.",101)
		logging("Received clusterId ${map.clusterId} command ${map.command} with ${receivedData.length} values: ${receivedData}",101)
		logging("Splurge! ${map}",101)

	}

	return null

}

void parseAndSendBatteryStatus(BigDecimal vCurrent) {

	BigDecimal bat = 0
	BigDecimal vMin = vMinSetting == null ? 2.9 : vMinSetting
	BigDecimal vMax = vMaxSetting == null ? 3.9 : vMaxSetting    

	if(vMax - vMin > 0) {
		bat = ((vCurrent - vMin) / (vMax - vMin)) * 100.0
	} else {
		bat = 100
	}
	bat = bat.setScale(0, BigDecimal.ROUND_HALF_UP)
	bat = bat > 100 ? 100 : bat
	
	vCurrent = vCurrent.setScale(3, BigDecimal.ROUND_HALF_UP)

	logging("${device} : Battery : $bat% ($vCurrent V)", 100)
	sendEvent(name:"battery",value:bat,unit: "%", isStateChange: false)
	sendEvent(name:"batteryWithUnit",value:"${bat} %",isStateChange: false)

}

private boolean logging(message, level) {

	boolean didLog = false
	 
	Integer logLevelLocal = 0

	if (infoLogging == null || infoLogging == true) {
		logLevelLocal = 100
	}

	if (debugLogging == true) {
		logLevelLocal = 1
	}
	 
	if (logLevelLocal != 0){

		switch (logLevelLocal) {
			case 1:  
				if (level >= 1 && level < 99) {
					log.debug "$message"
					didLog = true
				} else if (level == 100) {
					log.info "$message"
					didLog = true
				} else if (level > 100) {
					log.warn "$message"
					didLog = true
				}
			break
			case 100:  
				if (level == 100) {
					log.info "$message"
					didLog = true
				} else if (level > 100) {
					log.warn "$message"
					didLog = true
				}
			break
		}

	}

	return didLog

}
