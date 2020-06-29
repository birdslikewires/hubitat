/*
 * 
 *  Driver for Salus SP600 Smart Plug
 *	
 */


metadata {

	definition (name: "Salus SP600 Smart Plug v2", namespace: "Salus", author: "Salus") {

		capability "Configuration"
		capability "Switch"
		capability "Power Meter"
		capability "Refresh"

		fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0004, 0005, 0006, 0402, 0702, FC01", outClusters: "0019", manufacturer: "Computime", model: "SP600", deviceJoinName: "Salus SP600 Smart Plug"
	}

}


preferences {
	
	input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	input name: "ReportMin", type: "number", title: "Minimum Report Time (seconds)", defaultValue: 1
	input name: "ReportDelta", type: "long", title: "Minimum Report Watt change 1 to 10", defaultValue: 5
}



def initialize() {
	
	log.warn "Initialize Called!"

	configure()
	
}

def logsOff(){

	log.warn "debug logging disabled!"

	device.updateSetting("logEnable",[value:"false",type:"bool"])

}

def updated(){

	log.info "Updated Called!"

	log.warn "debug logging is: ${logEnable == true}"
	log.warn "description logging is: ${txtEnable == true}"
	
	if (logEnable) runIn(1800,logsOff)

}

def parse(String description) {

	if (logEnable) {
		
		log.debug "Parse Called!"
		log.debug "description is $description"

	}
	
	def eventMap = zigbee.getEvent(description)

	if (!eventMap) {
		
		eventMap = getDescription(zigbee.parseDescriptionAsMap(description))	

	}

	if (eventMap) {
	
		if (txtEnable) log.info "$device eventMap name: ${eventMap.name} value: ${eventMap.value}"

		sendEvent(eventMap)

	}
	else {
		
//		log.warn "DID NOT PARSE MESSAGE for description: $description"			
		
		def descriptionMap = zigbee.parseDescriptionAsMap(description)
		if (logEnable) log.debug "descriptionMAp: $descriptionMap"			

	}	

}

def off() {

	zigbee.off()

}

def on() {

	zigbee.on()

}

def refresh() {
	
	if (logEnable) log.debug "Refresh Called!"
	
	zigbee.onOffRefresh() + simpleMeteringPowerRefresh()

}

def configure() {

	if (logEnable) log.debug "Configure Called!"

	zigbee.onOffConfig() + simpleMeteringPowerConfig() + zigbee.onOffRefresh() + simpleMeteringPowerRefresh()

}

def simpleMeteringPowerRefresh() {

	zigbee.readAttribute(0x0702, 0x0400)

}

def simpleMeteringPowerConfig(minReportTime=ReportMin.toInteger(), maxReportTime=600, reportableChange=ReportDelta.toInteger()) {

	zigbee.configureReporting(0x0702, 0x0400, DataType.INT24, minReportTime, maxReportTime, reportableChange)

}

def getDescription(descMap) {

	def powerValue = "undefined"

	if (descMap.cluster == "0702") {

		if (descMap.attrId == "0400") {

			if(descMap.value != "ffff") powerValue = zigbee.convertHexToInt(descMap.value)

		}

	}
	else if (descMap.clusterId == "0702") {

		if(descMap.command == "07"){

			return	[name: "update", value: "power (0702) capability configured successfully"]

		}

	}
	else if (descMap.clusterId == "0006") {

		if(descMap.command == "07"){

			return	[name: "update", value: "switch (0006) capability configured successfully"]

		}

	}

	if (powerValue != "undefined"){

		return	[name: "power", value: powerValue]

	}
	else {

		return null

	}

}
