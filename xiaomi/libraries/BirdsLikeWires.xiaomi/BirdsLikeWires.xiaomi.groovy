/*
 * 
 *  BirdsLikeWires Xiaomi Library v1.02 (10th October 2022)
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

	if (description.indexOf('encoding: 10') >= 0 || description.indexOf('encoding: 20') >= 0) {

		// Normal encoding should bear some resemblance to the Zigbee Cluster Library Specification
		descriptionMap = zigbee.parseDescriptionAsMap(description)

	} else {

		// Anything else is specific to Xiaomi, so we'll just slice and dice the string we receive.
		descriptionMap = description.split(', ').collectEntries {
			entry -> def pair = entry.split(': ')
			[(pair.first()): pair.last()]
		}

	}

	if (descriptionMap) {

		if (descriptionMap.cluster == "0000" && descriptionMap.attrId == "FF01") { 

			// Device Status Cluster
			xiaomiDeviceStatus(descriptionMap)

		} else {

			// Hand back to the driver for processing.
			// Only clusters with content unique to a device should be passed back.
			processMap(descriptionMap)

		}

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Splurge! : ${description}", "warn")

	}

}


void xiaomiDeviceStatus(Map map) {

	// FF01 - Device Status Cluster

	String modelCheck = "${getDeviceDataByName('model')}"

	def deviceData = ""
	def dataSize = map.value.size()

	if (dataSize > 20) {
		deviceData = map.value
	} else {
		logging("${device} : deviceData : No device information in this report.", "debug")
		return
	}
	
	// Report the battery voltage and calculated percentage.
	def batteryVoltageHex = "undefined"
	BigDecimal batteryVoltage = 0

	batteryVoltageHex = deviceData[8..9] + deviceData[6..7]
	logging("${device} : batteryVoltageHex : ${batteryVoltageHex}", "trace")

	batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex)
	logging("${device} : batteryVoltage sensor value : ${batteryVoltage}", "debug")

	batteryVoltage = batteryVoltage.setScale(2, BigDecimal.ROUND_HALF_UP) / 1000

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

	//// On some devices (buttons for one) there's a wildly inaccurate temperature sensor.
	//// Leaving this here so that we know how to read it in the future, should we wish to.

	// Report the temperature in celsius.
	// def temperatureValue = "undefined"
	// temperatureValue = deviceData[14..15]
	// logging("${device} : temperatureValue : ${temperatureValue}", "trace")
	// BigDecimal temperatureCelsius = hexToBigDecimal(temperatureValue)

	// logging("${device} : temperatureCelsius sensor value : ${temperatureCelsius}", "trace")
	// logging("${device} : Temperature : $temperatureCelsius °C", "info")
	// sendEvent(name: "temperature", value: temperatureCelsius, unit: "C")

}


void xiaomiSkip(String clusterId) {

	logging("${device} : Skipping data received on clusterId ${clusterId}.", "debug")

}
