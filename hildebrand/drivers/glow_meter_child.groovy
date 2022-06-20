/*
 * 
 *  Hildebrand Glow Meter Child Driver v1.01 (20th June 2022)
 *	
 */


metadata {

	definition (name: "Hildebrand Glow Meter", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hildebrand/drivers/glow_meter_child.groovy") {

		capability "Initialize"

		capability "EnergyMeter"
		capability "PowerMeter"

		attribute "day", "decimal"
		attribute "week", "decimal"
		attribute "month", "decimal"

		attribute "unitrate", "decimal"
		attribute "standingcharge", "decimal"

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
