/*
 * 
 *  Driver for AlertMe Lamp Device
 *	
 */


metadata {

	definition (name: "AlertMe Lamp Device", namespace: "AlertMe", author: "Andrew Davison") {

		capability "Battery"
		capability "Configuration"
		capability "Switch"
		capability "Refresh"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00F5", outClusters: "", manufacturer: "AlertMe.com", model: "Lamp Device", deviceJoinName: "AlertMe Lamp"
	}

}


preferences {
	
	input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
	
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

	log.info "Update called!"

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
		
		log.warn "DID NOT PARSE MESSAGE for description: $description"			
		
		def descriptionMap = zigbee.parseDescriptionAsMap(description)
		if (logEnable) log.debug "descriptionMap: $descriptionMap"			

	}	

}

def off() {

	if (logEnable) log.debug "Turn it on."

	zigbee.off()

}

def on() {

	if (logEnable) log.debug "Turn it off."


	zigbee.on()

}

def refresh() {
	
	if (logEnable) log.debug "Refresh Called!"
	
	if (logEnable) log.debug "Trying randomRefreshAttempt..."
	randomRefreshAttempt()

	//zigbee.onOffRefresh() + simpleMeteringPowerRefresh()

}

def configure() {

	if (logEnable) log.debug "Configure Called! But I don't do anything right now."

	//zigbee.onOffConfig() + simpleMeteringPowerConfig() + zigbee.onOffRefresh() + simpleMeteringPowerRefresh()



}

def randomRefreshAttempt() {

	zigbee.readAttribute(0x00F5, 0x0400)

}

def simpleMeteringPowerConfig(minReportTime=1, maxReportTime=600, reportableChange=0x05) {

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
