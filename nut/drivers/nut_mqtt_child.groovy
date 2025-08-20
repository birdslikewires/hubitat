/*
 * 
 *  Network UPS Tools MQTT Child Driver
 *	
 */


@Field String driverVersion = "v1.01 (20th August 2025)"
@Field boolean debugMode = false

import groovy.transform.Field

@Field String deviceName = "Network UPS Tools MQTT Device"


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/hildebrand/drivers/glow_meter_child.groovy") {

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
