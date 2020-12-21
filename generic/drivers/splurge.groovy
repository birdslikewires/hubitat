/*
 * 
 *  Splurge Driver v1.01 (16th September 2020)
 *	
 */


metadata {

	definition (name: "Splurge Driver", namespace: "AlertMe", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/drivers/splurge.groovy") {

		capability "Battery"
		capability "Initialize"
		capability "Power Meter"
		capability "Refresh"
		capability "Temperature Measurement"

		//fingerprint profileId: "C216", inClusters: "00F0,00F3,00EF", outClusters: "", manufacturer: "AlertMe.com", model: "Power Clamp", deviceJoinName: "AlertMe Power Clamp"
		
	}

}


preferences {
	
	input name: "infoLogging",type: "bool",title: "Enable logging",defaultValue: true
	input name: "debugLogging",type: "bool",title: "Enable debug logging",defaultValue: true
	input name: "silenceLogging",type: "bool",title: "Force silent mode (overrides log settings)",defaultValue: false
	
}


def installed() {
	// Runs after pairing.
	logging("${device} : Installing",100)
}


def initialize() {
	configure()
}


def configure() {
	// Runs after installed() whenever a device is paired or rejoined.

	device.updateSetting("infoLogging",[value:"true",type:"bool"])
	device.updateSetting("debugLogging",[value:"true",type:"bool"])
	device.updateSetting("silenceLogging",[value:"false",type:"bool"])

	// Remove any scheduled events.
	unschedule()

	// Bunch of zero or null values.
	//sendEvent(name:"battery",value:0, unit: "%", isStateChange: false)

	// Schedule our refresh check-in and turn off the logs.
	//randomMinute = Math.abs(new Random().nextInt() % 60)
	//schedule("0 ${randomMinute} 0/1 1/1 * ? *", rangeAndRefresh)
	//runIn(300,debugLogOff)
	//runIn(600,infoLogOff)

	// Report our logging status.
	loggingStatus()

	// All done.
	logging("${device} : Configured",100)
	
}


def updated() {
	// Runs whenever preferences are saved.
	loggingStatus()
	refresh()
}


void loggingStatus() {
	logging("${device} : Logging : ${infoLogging == true}",200)
	logging("${device} : Debug Logging : ${debugLogging == true}",200)
	logging("${device} : Silent Mode : ${silenceLogging == true}",200)
}


void reportToDev(data,map) {

	logging("${device} : Unknown data! Please report this to the developer.",201)
	logging("${device} : Received clusterId ${map.clusterId} command ${map.command} with ${data.length} values: ${data}",201)
	logging("${device} : Splurge! ${map}",201)

}


void debugLogOff(){
	
	logging("${device} : Debug Logging : false",200)
	device.updateSetting("debugLogging",[value:"false",type:"bool"])

}


void infoLogOff(){
	
	logging("${device} : Logging : false",200)
	device.updateSetting("infoLogging",[value:"false",type:"bool"])

}


void refresh() {
	logging("${device} : Refreshed",100)
}


def parse(String description) {
		
	def descriptionMap = zigbee.parseDescriptionAsMap(description)
	
	if (descriptionMap) {
	
		logging("${device} : Splurge!: ${descriptionMap}",109)
		outputValues(descriptionMap)

	} else {
		
		logging("${device} : PARSE FAILED : $description",101)

	}	

}


def outputValues(map) {

	String[] receivedData = map.data

	if (map.clusterId == "FFFF") {

		// Cluster FFFF is fake.

		if (map.command == "FF") {

			// Command FF is also fake.

			def powerValueHex = "undefined"
			def int powerValue = 0

			powerValueHex = receivedData[0..1].reverse().join()
			logging("${device} : power byte flipped : ${powerValueHex}",109)
			powerValue = zigbee.convertHexToInt(powerValueHex)
			logging("${device} : power sensor reports : ${powerValue}",109)

			logging("${device} : Power Reading : ${powerValue} W",100)

			sendEvent(name: "power", value: powerValue, unit: "W", isStateChange: true)
			sendEvent(name: "powerWithUnit", value: "${powerValue} W", isStateChange: true)

		} else {

			// Unknown power usage data.
			reportToDev(receivedData,map)

		}

	} else {

		// Not a clue what we've received.
		reportToDev(receivedData,map)

	}

	return null

}


void parseAndSendBatteryPercentage(BigDecimal vCurrent) {

	BigDecimal bat = 0
	BigDecimal vMin = batteryVoltageMinimum == null ? 2.5 : batteryVoltageMinimum
	BigDecimal vMax = batteryVoltageMaximum == null ? 3.0 : batteryVoltageMaximum    

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

	// 100 = Normal logging, suppressed by user preference and silent mode.
	// 101 = Normal warning, suppressed by user preference and silent mode.
	// 109 = Normal debug, suppressed by user preference and silent mode.
	// 200 = Critical logging, ignores user preference but respects silent mode.
	// 201 = Critical warning, ignores all preferences.
	// 209 = Critical debug, ignores user preference but respects silent mode.

	// Critical warnings are always allowed.
	if (level == 201) {
		log.warn "$message"
		didLog = true
	}

	if (!silenceLogging) {

		// Standard logging will obey the log preferences, except for warnings, which are allowed.
		if (level == 101) {
			log.warn "$message"
			didLog = true
		} else if (level == 100 || level == 109) {
			if (level == 100) {
				if (infoLogging) {
					log.info "$message"
					didLog = true
				}
			} else {
				if (debugLogging) {
					log.debug "$message"
					didLog = true
				}
			}
		}

		// Critical logging for non-repeating events will be allowed through.
		if (level == 200 || level == 209) {
			if (level == 200) {
				log.info "$message"
				didLog = true
			} else {
				log.debug "$message"
				didLog = true
			}
		}

	}

	return didLog

}
