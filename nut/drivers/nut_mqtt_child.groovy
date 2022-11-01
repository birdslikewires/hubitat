/*
 * 
 *  Network UPS Tools MQTT Child Driver v1.00 (1st November 2022)
 *	
 */


metadata {

	definition (name: "Network UPS Tools MQTT Device", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hildebrand/drivers/glow_meter_child.groovy") {

		capability "Initialize"

		//capability "Battery"
		capability "PowerSource"
		capability "VoltageMeasurement"

	}

}


void initialize() {

	state.clear()

}


void parse(List<Map> description) {

    description.each {
    	sendEvent(it)
    }

}


void setState(List<Map> description) {

    description.each {
		state."${it.name}" = it.value
    }

}
