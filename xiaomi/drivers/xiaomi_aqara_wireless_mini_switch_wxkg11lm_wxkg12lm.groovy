/*
 * 
 *  Xiaomi Aqara Wireless Mini Switch WXKG11LM / WXKG12LM Driver v1.04 (26th June 2022)
 *	
 */


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 50


metadata {

	definition (name: "Xiaomi Aqara Wireless Mini Switch WXKG11LM / WXKG12LM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_aqara_wireless_mini_switch_wxkg11lm_wxkg12lm.groovy") {

		capability "AccelerationSensor"
		capability "Battery"
		capability "Configuration"
		capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "ReleasableButton"
		capability "SwitchLevel"
		//capability "TemperatureMeasurement"	// Just because you can doesn't mean you should.

		attribute "batteryState", "string"
		attribute "batteryVoltage", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,FFFF,0006", outClusters: "0000,0004,FFFF", manufacturer: "LUMI", model: "lumi.sensor_switch.aq2", deviceJoinName: "WXKG11LMr1"
		fingerprint profileId: "0104", inClusters: "0000,0012,0003", outClusters: "0000", manufacturer: "LUMI", model: "lumi.remote.b1acn01", deviceJoinName: "WXKG11LMr2"
		fingerprint profileId: "0104", inClusters: "0000,0012,0006,0001", outClusters: "0000", manufacturer: "LUMI", model: "lumi.sensor_switch.aq3", deviceJoinName: "WXKG12LM"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


def testCommand() {
	logging("${device} : Test Command", "info")
}


def installed() {
	// Runs after first installation.
	logging("${device} : Installed", "info")
	configure()
}


def configure() {

	// Tidy up.
	unschedule()

	state.clear()
	state.presenceUpdated = 0
	
	sendEvent(name: "acceleration", value: "inactive", isStateChange: false)
	sendEvent(name: "level", value: 0, isStateChange: false)
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Set default preferences.
	device.updateSetting("infoLogging", [value: "true", type: "bool"])
	device.updateSetting("debugLogging", [value: "${debugMode}", type: "bool"])
	device.updateSetting("traceLogging", [value: "${debugMode}", type: "bool"])

	// Schedule reporting and presence checking.
	int randomSixty
	
	int checkEveryMinutes = 10					
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Set device name.
	device.name = "Xiaomi Aqara Wireless Mini Switch WXKG12LM"

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


void push(buttonId) {
	
	sendEvent(name:"pushed", value: buttonId, isStateChange:true)
	
}


void doubleTap(buttonId) {
	
	sendEvent(name:"doubleTapped", value: buttonId, isStateChange:true)
	
}


void hold(buttonId) {
	
	sendEvent(name:"held", value: buttonId, isStateChange:true)
	
}


void release(buttonId) {
	
	sendEvent(name:"released", value: buttonId, isStateChange:true)
	
}


void setLevel(BigDecimal level) {

	setLevel(level,1)

}


void setLevel(BigDecimal level, BigDecimal duration) {

	BigDecimal safeLevel = level <= 100 ? level : 100
	safeLevel = safeLevel < 0 ? 0 : safeLevel

	String hexLevel = percentageToHex(safeLevel.intValue())

	BigDecimal safeDuration = duration <= 25 ? (duration*10) : 255
	String hexDuration = Integer.toHexString(safeDuration.intValue())

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Got level request of '${level}' (${safeLevel}%) [${hexLevel}] changing over '${duration}' second${pluralisor} (${safeDuration} deciseconds) [${hexDuration}].", "debug")

	sendEvent(name: "level", value: "${safeLevel}")

}


void accelerationInactive() {

	sendEvent(name: "acceleration", value: "inactive", isStateChange: true)

}


void parse(String description) {

	// Primary parse routine.

	logging("${device} : Parse : $description", "debug")

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

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Splurge! : ${description}", "warn")

	}

}


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedData = map.data

	if (map.cluster == "0006") { 
		// Here we handle button presses for the WXKG11LM 

		int buttonNumber = map.value[-1..-1].toInteger()
		buttonNumber = buttonNumber == 0 ? 1 : buttonNumber

		if (buttonNumber == 2) {
			logging("${device} : Trigger : Button Double Tapped", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
		}

		logging("${device} : Trigger : Button ${buttonNumber} Pressed", "info")
		sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)

	} else if (map.cluster == "0012") { 
		// Here we handle button presses for the WXKG12LM and holds for WXKG11LMr2

		if (map.value == "0100") {

			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else if (map.value == "0200") {

			logging("${device} : Trigger : Button Double Tapped", "info")
			sendEvent(name: "doubleTapped", value: 1, isStateChange: true)
			sendEvent(name: "pushed", value: 2, isStateChange: true)

		} else if (map.value == "0000" || map.value == "1000") {

			state.changeLevelStart = now()
			logging("${device} : Trigger : Button Held", "info")
			sendEvent(name: "held", value: 1, isStateChange: true)
			sendEvent(name: "pushed", value: 3, isStateChange: true)

		} else if (map.value == "1100" || map.value == "FF00") {

			logging("${device} : Trigger : Button Released", "info")
			sendEvent(name: "released", value: 1, isStateChange: true)
			sendEvent(name: "pushed", value: 4, isStateChange: true)

			// Now work out the level we should report based upon the hold duration.

			long millisHeld = now() - state.changeLevelStart
			if (millisHeld > 6000) {
				millisHeld = 0				// In case we don't receive a 'released' message.
			}

			BigInteger levelChange = 0
			levelChange = millisHeld / 6000 * 140
			// That multiplier above is arbitrary - it was 100, but has been increased to account for the delay in detecting hold mode.

			BigDecimal secondsHeld = millisHeld / 1000
			secondsHeld = secondsHeld.setScale(2, BigDecimal.ROUND_HALF_UP)

			logging("${device} : Level : Setting level to ${levelChange} after holding for ${secondsHeld} seconds.", "info")

			setLevel(levelChange)

		} else if (map.value == "1200") {

			logging("${device} : Trigger : Button Shaken", "info")
			sendEvent(name: "acceleration", value: "active", isStateChange: true)
			sendEvent(name: "pushed", value: 6, isStateChange: true)
			runIn(4,accelerationInactive)

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "0000") { 

		if (map.attrId == "0005" || map.attrId == "FF01") {

			def deviceData = ""

			if (map.attrId == "0005") {

				// Grab battery data triggered by short press of the reset button.
				deviceData = map.value.split('FF42')[1]

				// Scrounge more value! It's another button, so as these can quad-click we'll call this button five.
				logging("${device} : Trigger : Button 5 Pressed", "info")
				sendEvent(name: "pushed", value: 5, isStateChange: true)

			} else {

				// We get data with an attrId of FF01 when pressed more than twice, but only the check-in reports contain battery info.
				def dataSize = map.value.size()

				if (dataSize > 20) {
					deviceData = map.value
				} else {
					logging("${device} : deviceData : No battery information in this report.", "debug")
					return
				}
				
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
			sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V")

			BigDecimal batteryPercentage = 0
			BigDecimal batteryVoltageScaleMin = 2.1
			BigDecimal batteryVoltageScaleMax = 3.0

			if (batteryVoltage >= batteryVoltageScaleMin) {

				state.batteryOkay = true

				batteryPercentage = ((batteryVoltage - batteryVoltageScaleMin) / (batteryVoltageScaleMax - batteryVoltageScaleMin)) * 100.0
				batteryPercentage = batteryPercentage.setScale(0, BigDecimal.ROUND_HALF_UP)
				batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage

				if (batteryPercentage > 20) {
					logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "info")
				} else {
					logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
				}

				sendEvent(name: "battery", value:batteryPercentage, unit: "%")
				sendEvent(name: "batteryState", value: "discharging")

			} else {

				// Very low voltages indicate an exhausted battery which requires replacement.

				state.batteryOkay = false

				batteryPercentage = 0

				logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
				sendEvent(name: "battery", value:batteryPercentage, unit: "%")
				sendEvent(name: "batteryState", value: "exhausted")

			}

			// Report the temperature in celsius.
			// def temperatureValue = "undefined"
			// temperatureValue = deviceData[14..15]
			// logging("${device} : temperatureValue : ${temperatureValue}", "trace")
			// BigDecimal temperatureCelsius = hexToBigDecimal(temperatureValue)

			// logging("${device} : temperatureCelsius sensor value : ${temperatureCelsius}", "trace")
			// logging("${device} : Temperature : $temperatureCelsius °C", "info")
			// sendEvent(name: "temperature", value: temperatureCelsius, unit: "C")

		} else {

			// processBasic(map)
			reportToDev(map)

		}

	} else {

		reportToDev(map)

	}

}
