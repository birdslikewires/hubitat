/*
 * 
 *  BirdsLikeWires Xiaomi Library v1.12 (8th November 2022)
 *	
 */


library (

	author: "Andrew Davison",
	category: "zigbee",
	description: "Library methods used by BirdsLikeWires Xiaomi drivers.",
	documentationLink: "https://github.com/birdslikewires/hubitat",
	name: "xiaomi",
	namespace: "BirdsLikeWires"

)


void installed() {

	// Runs after first installation.
	logging("${device} : Installed", "info")
	configure()

}


void configure() {

	int randomSixty

	// Tidy up.
	unschedule()
	state.clear()
	state.presenceUpdated = 0
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Schedule presence checking.
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Set device specifics.
	updateDataValue("driver", "$driverVersion")
	configureSpecifics()

	// Notify.
	sendEvent(name: "configuration", value: "complete", isStateChange: false)
	logging("${device} : Configuration complete.", "info")

	updated()
	
}


void updated() {
	// Runs when preferences are saved.

	unschedule(infoLogOff)
	unschedule(debugLogOff)
	unschedule(traceLogOff)

	if (!debugMode) {
		runIn(2400,debugLogOff)
		runIn(1200,traceLogOff)
	}

	logging("${device} : Preferences Updated", "info")

	loggingStatus()

}


void refresh() {

	logging("${device} : Refreshing", "info")

}


void parse(String description) {

	updatePresence()

	String encodingCheck = "unknown"
	encodingCheck = "${getDeviceDataByName('encoding')}"

	Map descriptionMap = null

	if (encodingCheck == "Xiaomi") {

		// Most Xiaomi devices don't follow the spec, so we slice-and-dice the string we receive.
		descriptionMap = description.split(', ').collectEntries {
			entry -> def pair = entry.split(': ')
			[(pair.first()): pair.last()]
		}

	} else if (encodingCheck == "Zigbee") {

		// These devices appear to follow the Zigbee Cluster Library Specification
		descriptionMap = zigbee.parseDescriptionAsMap(description)

	} else {

		logging("${device} : Parse : Cannot parse message, encoding type is $encodingCheck.", "error")
		logging("${device} : Parse : Attempting to configure device.", "info")
		configure()
		return

	}

	logging("${device} : Parse : Interpreting against $encodingCheck cluster specification.", "debug")

	if (descriptionMap) {

		logging("${device} : Parse : ${descriptionMap}", "debug")

		if (descriptionMap.cluster == "0000" && descriptionMap.attrId == "FF01") { 

			// Device Status Cluster
			xiaomiDeviceStatus(descriptionMap)

		} else {

			// Hand back to the driver for processing.
			processMap(descriptionMap)

		}

	} else {
		
		logging("${device} : Parse : Failed to parse $encodingCheck cluster specification data. Please report these messages to the developer.", "error")
		logging("${device} : Parse : ${description}", "error")

	}

	String versionCheck = "unknown"
	versionCheck = "${getDeviceDataByName('driver')}"

	if ("$versionCheck" != "$driverVersion") {

		logging("${device} : Driver : Updating configuration from $versionCheck to $driverVersion.", "info")
		configure()

	}

}


void xiaomiDeviceStatus(Map map) {

	int batteryDivisor = 1
	String batteryVoltageHex = "undefined"
	String modelCheck = "${getDeviceDataByName('model')}"
	def dataSize = map.value.size()

        logging("${device} check-in message.", "info")
	logging("${device} : xiaomiDeviceStatus : Received $dataSize character message.", "debug")

	if (modelCheck == "lumi.sen_ill.mgl01") {
		// The Mijia Smart Light Sensor neatly reports its battery hex values on attrId 0020 of cluster 0001.

		batteryVoltageHex = map.value
		batteryDivisor = 10

	} else {
		// Everything else mushes it into the status data on attrId FF01 of cluster 0000.

		if (dataSize > 20) {

			batteryVoltageHex = map.value[8..9] + map.value[6..7]
			batteryDivisor = 1000

		} else {

			logging("${device} : xiaomiDeviceStatus : No device information in this $dataSize character message.", "debug")
			return

		}

	}
	
	reportBattery(batteryVoltageHex, batteryDivisor, 2.8, 3.0)

    try {

        if (modelCheck == "lumi.weather") {
            // decode sensor values, which are part of the checkin message
            parseCheckinMessageSpecifics(map.value)
        } else {
            // On some devices (buttons for one) there's a wildly inaccurate temperature sensor.
            // We may as well throw this out in the log for comedy value as it's rarely reported.
            // Who knows. We may learn something.

            String temperatureValue = "undefined"
            temperatureValue = map.value[14..15]
            BigDecimal temperatureCelsius = hexToBigDecimal(temperatureValue)
            
            logging("${device} : temperatureValue : ${temperatureValue}", "trace")
            logging("${device} : temperatureCelsius sensor value : ${temperatureCelsius}", "trace")

            logging("${device} : Inaccurate Temperature : $temperatureCelsius °C", "info")
            // sendEvent(name: "temperature", value: temperatureCelsius, unit: "C")			// No, don't do that. That would be silly.
        }
    } catch (Exception e) {

        return

    }
}
