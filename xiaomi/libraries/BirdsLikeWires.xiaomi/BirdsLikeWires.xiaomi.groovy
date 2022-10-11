/*
 * 
 *  BirdsLikeWires Xiaomi Library v1.06 (11th October 2022)
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

	Map descriptionMap = null
	String parseType = "Zigbee"

	if (description.indexOf('catchall:') >= 0 || description.indexOf('encoding: 10') >= 0 || description.indexOf('encoding: 20') >= 0 || description.indexOf('encoding: 21') >= 0) {

		// Normal encoding should bear some resemblance to the Zigbee Cluster Library Specification
		logging("${device} : Parse : Processing against Zigbee cluster specification.", "debug")
		descriptionMap = zigbee.parseDescriptionAsMap(description)

	} else {

		// Anything else is specific to Xiaomi, so we'll just slice and dice the string we receive.
		logging("${device} : Parse : Processing what we're assuming is Xiaomi structured data.", "debug")
		descriptionMap = description.split(', ').collectEntries {
			entry -> def pair = entry.split(': ')
			[(pair.first()): pair.last()]
		}
		parseType = "Xiaomi"

	}

	if (descriptionMap) {

		logging("${device} : Parse : ${descriptionMap}", "debug")

		if (descriptionMap.cluster == "0000" && descriptionMap.attrId == "FF01") { 

			// Device Status Cluster
			xiaomiDeviceStatus(descriptionMap)

		} else if (descriptionMap.clusterId == "8004") {
		
			processDescriptors(descriptionMap)

		} else {

			// Hand back to the driver for processing.
			// Only clusters with content unique to a device should be passed back.
			processMap(descriptionMap)

		}

	} else {
		
		logging("${device} : Parse : Failed to parse ${parseType} specification data. Please report these messages to the developer.", "warn")
		logging("${device} : Parse : Failed Here : ${description}", "warn")

	}

}


void xiaomiDeviceStatus(Map map) {

	// Device Status

	int batteryDivisor = 1
	String batteryVoltageHex = "undefined"
	String modelCheck = "${getDeviceDataByName('model')}"

	if (modelCheck == "lumi.sen_ill.mgl01") {
		// The Mijia Smart Light Sensor neatly reports its battery hex values on attrId 0020 of cluster 0001.

		batteryVoltageHex = map.value
		batteryDivisor = 10

	} else {
		// Everything else mushes it into the status data on attrId FF01 of cluster 0000.

		if (map.value.size > 20) {

			batteryVoltageHex = map.value[8..9] + map.value[6..7]
			batteryDivisor = 1000

		} else {

			logging("${device} : xiaomiDeviceStatus : No device information in this report.", "debug")
			return

		}

	}
	
	// Report the battery voltage and calculated percentage.
	BigDecimal batteryVoltage = 0

	logging("${device} : batteryVoltageHex : ${batteryVoltageHex}", "trace")

	batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex)
	logging("${device} : batteryVoltage sensor value : ${batteryVoltage}", "debug")

	batteryVoltage = batteryVoltage.setScale(2, BigDecimal.ROUND_HALF_UP) / batteryDivisor

	logging("${device} : batteryVoltage : ${batteryVoltage}", "debug")
	sendEvent(name: "voltage", value: batteryVoltage, unit: "V")

	BigDecimal batteryPercentage = 0
	BigDecimal batteryVoltageScaleMin = 2.1
	BigDecimal batteryVoltageScaleMax = 3.0

	if (batteryVoltage >= batteryVoltageScaleMin) {

		batteryPercentage = ((batteryVoltage - batteryVoltageScaleMin) / (batteryVoltageScaleMax - batteryVoltageScaleMin)) * 100.0
		batteryPercentage = batteryPercentage.setScale(0, BigDecimal.ROUND_HALF_UP)
		batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage

		if (batteryPercentage > 20) {
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "info")
		} else {
			logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
		}

		sendEvent(name: "battery", value:batteryPercentage, unit: "%")
		state.battery = "discharging"

	} else {

		// Very low voltages indicate an exhausted battery which requires replacement.

		batteryPercentage = 0

		logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
		logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
		sendEvent(name: "battery", value:batteryPercentage, unit: "%")
		state.battery = "exhausted"

	}

	// On some devices (buttons for one) there's a wildly inaccurate temperature sensor.
	// We may as well throw this out in the log for comedy value as it's rarely reported.
	// Who knows. We may learn something.

	def temperatureValue = "undefined"
	temperatureValue = deviceData[14..15]
	logging("${device} : temperatureValue : ${temperatureValue}", "trace")

	BigDecimal temperatureCelsius = hexToBigDecimal(temperatureValue)
	logging("${device} : temperatureCelsius sensor value : ${temperatureCelsius}", "trace")
	logging("${device} : Inaccurate Temperature : $temperatureCelsius °C", "info")
	// sendEvent(name: "temperature", value: temperatureCelsius, unit: "C")

}


void xiaomiSkip(String clusterId) {

	logging("${device} : Skipping data received on clusterId ${clusterId}.", "debug")

}
