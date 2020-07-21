/*
 * 
 *  AlertMe Lamp Driver v1.02 (21st July 2020)
 *	
 */


metadata {

	definition (name: "AlertMe Lamp", namespace: "AlertMe", author: "Andrew Davison") {

		capability "Battery"
		capability "Initialize"
		capability "Refresh"
		capability "Temperature Measurement"

		command "normalMode"
		command "rangingMode"

		attribute "batteryVoltage", "string"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "mode", "string"
		attribute "rssi", "string"
		attribute "temperatureWithUnit", "string"
		attribute "uptime", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F5", outClusters: "", manufacturer: "AlertMe.com", model: "Lamp Device", deviceJoinName: "AlertMe Lamp"

	}

}


preferences {
	
	input name: "vMinSetting",type: "decimal",title: "Battery Minimum Voltage",description: "Low battery voltage (default: 3.0)",defaultValue: "2.5",range: "2.1..2.8"
	input name: "vMaxSetting",type: "decimal",title: "Battery Maximum Voltage",description: "Full battery voltage (default: 3.6)",defaultValue: "3.0",range: "2.9..3.4"
	input name: "infoLogging",type: "bool",title: "Enable logging",defaultValue: true
	input name: "debugLogging",type: "bool",title: "Enable debug logging",defaultValue: false
	
}


def initialize() {
	
	state.operatingMode = "normal"
	
	// Remove any scheduled events.
	unschedule()

	// Bunch of zero or null values.
	sendEvent(name:"battery",value:0, unit: "%", isStateChange: false)
	sendEvent(name:"batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name:"batteryVoltageWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name:"batteryWithUnit", value: "unknown",isStateChange: false)
	sendEvent(name:"mode", value: "unknown",isStateChange: false)
	sendEvent(name:"rssi", value: "unknown")
	sendEvent(name:"temperature", value: 0, unit: "C", isStateChange: false)
	sendEvent(name:"temperatureWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name:"uptime", value: 0, unit: "s", isStateChange: false)

	// Schedule our refresh check-in and turn off the logs.
	randomMinute = Math.abs(new Random().nextInt() % 60)
	schedule("0 ${randomMinute} 0/1 1/1 * ? *", rangeAndRefresh)
	runIn(300,debugLogOff)
	runIn(600,infoLogOff)

	// Report our logging status.
	logging("Info logging is: ${infoLogging == true}",101)
	logging("Debug logging is: ${debugLogging == true}",101)

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


def refresh() {

	// Ssh. We don't really do anything here.
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

	if (map.clusterId == "00F0") {

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

	} else if (map.clusterId == "00F2") {

		logging("Received tamper button status.",101)
		logging("Received clusterId ${map.clusterId} command ${map.command} with ${receivedData.length} values: ${receivedData}",101)

	} else if (map.clusterId == "00F3") {

		logging("Received state change.",101)
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
	BigDecimal vMin = vMinSetting == null ? 2.5 : vMinSetting
	BigDecimal vMax = vMaxSetting == null ? 3.0 : vMaxSetting    

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
