/*
 * 
 *  Hildebrand Glow Meter Child Driver v1.00 (15th June 2022)
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

		if (it.name in ["mpan","mprn","supplier"]) {

			state."${it.name}" = it.value

		} else {

    		sendEvent(it)

		}

    }
}
