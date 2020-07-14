/*
 * 
 *  AlertMe Power Clamp Driver v1.00 (13th July 2020)
 *	
 */


metadata {

	definition (name: "AlertMe Power Clamp", namespace: "AlertMe", author: "Andrew Davison") {

		capability "Battery"
		//capability "Configuration"
        capability "Power Meter"
		//capability "Refresh"
        capability "Temperature Measurement"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00EF", outClusters: "", manufacturer: "AlertMe.com", model: "Power Clamp", deviceJoinName: "AlertMe Power Clamp"
        
	}

}


preferences {
	
	input name: "debugEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	input name: "logEnable", type: "bool", title: "Enable logging", defaultValue: true
	
}



def initialize() {
	
	configure()
	log.warn "Initialize Called!"
	
}

def logsOff(){

	device.updateSetting("debugEnable",[value:"false",type:"bool"])
	log.warn "Debug logging disabled!"

}

def updated(){

	log.info "Update called!"

	log.warn "debug logging is: ${debugEnable == true}"
	log.warn "description logging is: ${logEnable == true}"
	
	if (debugEnable) runIn(1800,logsOff)

}

def parse(String description) {
	
    def descriptionMap = zigbee.parseDescriptionAsMap(description)
    
	if (descriptionMap) {
	
        if (debugEnable) log.debug "splurge: ${descriptionMap}"        
		outputValues(descriptionMap)

	} else {
		
		log.warn "PARSE FAILED: $description"

	}	

}

def refresh() {
	
	if (debugEnable) log.debug "There's really no refresh trigger for this device."

}

def configure() {

	if (debugEnable) log.debug "There's really nothing to be configured on this device."

}

def randomRefreshAttempt() {

    def thing = "dunno"
    //thing = zigbee.readAttribute(0x00F0, 0x0000)
    
	if (debugEnable) log.debug thing

}

def outputValues(map) {

	String[] receivedData = map.data

	if (map.clusterId == "00EF") {

		// Cluster 00EF deals with power usage information.

		if (receivedData.length == 2) {

			// If we receive two 8-bit values, we know it's the instant reading. 

			def powerValue = "undefined"
			powerValue = receivedData[0..1].reverse().join()
			powerValue = zigbee.convertHexToInt(powerValue)
			if (logEnable) log.info "$device reports $powerValue W"

			return [name: "power", value: powerValue]

		} else if (receivedData.length == 9) {

			// Nine 8-bit values and we've received the power usage summary. Not seen anyone work this one out yet.

			log.info "Power usage summary data received, but we don't know how to decipher this yet."
			log.info "Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}"

		} else {

			log.warn "Unknown power usage information! Please report this to the developer."
			log.warn "Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}"

		}

	} else if (map.clusterId == "00F0") {

		// Cluster 00F0 deals with battery and temperature data.

		log.info "Battery and temperature data received, but we don't know how to decipher this yet."
		log.info "Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}"

	} else if (map.clusterId == "00F3") {

		log.info "I think this is the tamper button status. We don't know what to do with this yet."
		log.info "Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}"

	} else {

		log.warn "Unknown cluster received! Please report this to the developer."
		log.warn "Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}"

	}

	return null

}
