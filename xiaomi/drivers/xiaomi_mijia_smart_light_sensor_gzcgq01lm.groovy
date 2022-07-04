/*
 * 
 *  Xiaomi Mijia Smart Light Sensor GZCGQ01LM Driver v1.10 (5th July 2022)
 *	
 */


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 60
@Field int luxTolerance = 200


metadata {

	definition (name: "Xiaomi Mijia Smart Light Sensor GZCGQ01LM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_mijia_smart_light_sensor_gzcgq01lm.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "IlluminanceMeasurement"
		capability "PresenceSensor"
		capability "PushableButton"
		capability "Sensor"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "number"
		attribute "batteryVoltageWithUnit", "string"
		attribute "batteryWithUnit", "string"
		attribute "illuminanceDirection", "string"
		attribute "illuminanceWithUnit", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "LUMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "GZCGQ01LM"
		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "XIAOMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "GZCGQ01LM"

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
	state.rawLux = 0
	
	sendEvent(name: "illuminance", value: 0, unit: "lux")
	sendEvent(name: "illuminanceWithUnit", value: "0 lux")
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Set default preferences.
	device.updateSetting("infoLogging", [value: "true", type: "bool"])

	// Configure device reporting.
	int reportIntervalMinSeconds = 3
	int reportIntervalMaxSeconds = reportIntervalMinutes * 60

	ArrayList<String> cmds = [
		"zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0000 {${device.zigbeeId}} {}",
		"zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}",
		"zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0003 {${device.zigbeeId}} {}",
		"zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0400 {${device.zigbeeId}} {}",
	]
	cmds += zigbee.configureReporting(0x0001, 0x0020, 0x20, reportIntervalMaxSeconds, reportIntervalMaxSeconds, null)
    cmds += zigbee.configureReporting(0x0400, 0x0000, 0x21, reportIntervalMinSeconds, reportIntervalMaxSeconds, luxTolerance)
	sendZigbeeCommands(cmds)
 
	// Schedule presence checking.
 	int randomSixty
	int checkEveryMinutes = 10					
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Set device name.
	device.name = "Xiaomi Mijia Smart Light Sensor GZCGQ01LM"

	// Notify.
	sendEvent(name: "configuration", value: "set", isStateChange: false)
	logging("${device} : Configuration : Hub settings complete.", "info")

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


void parse(String description) {

	// Primary parse routine.

	updatePresence()

	logging("${device} : parse() : $description", "trace")

	Map descriptionMap = null
	String parseType = "Zigbee"

	if (description.indexOf('catchall:') >= 0 || description.indexOf('encoding: 10') >= 0 || description.indexOf('encoding: 20') >= 0 || description.indexOf('encoding: 21') >= 0) {

		// Normal encoding should bear some resemblance to the Zigbee Cluster Library Specification
		logging("${device} : Parse : Processing against Zigbee cluster specification.", "debug")
		descriptionMap = zigbee.parseDescriptionAsMap(description)

	} else {

		// Anything else is likely specific to Xiaomi, so we'll just slice and dice the string we receive.
		logging("${device} : Parse : Processing what we're assuming is Xiaomi structured data.", "debug")
		descriptionMap = description.split(', ').collectEntries {
			entry -> def pair = entry.split(': ')
			[(pair.first()): pair.last()]
		}
		parseType = "Xiaomi"

	}

	if (descriptionMap) {

		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse ${parseType} specification data. Please report these messages to the developer.", "warn")
		logging("${device} : Parse Failed Here : ${description}", "warn")

	}

}


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String receivedValue = map.value != null ? map.value : null

	if (map.cluster == "0000") {

		// processBasic(map)
		reportToDev(map)

	} else if (map.cluster == "0001") { 

		if (map.attrId == "0020") {
			
			// Report the battery voltage and calculated percentage.
			def batteryVoltageHex = "undefined"
			BigDecimal batteryVoltage = 0

			batteryVoltageHex = map.value
			logging("${device} : batteryVoltageHex : ${batteryVoltageHex}", "trace")

			batteryVoltage = zigbee.convertHexToInt(batteryVoltageHex)
			logging("${device} : batteryVoltage sensor value : ${batteryVoltage}", "debug")

			batteryVoltage = batteryVoltage.setScale(2, BigDecimal.ROUND_HALF_UP) / 10

			logging("${device} : batteryVoltage : ${batteryVoltage}", "debug")
			sendEvent(name: "batteryVoltage", value: batteryVoltage, unit: "V")
			sendEvent(name: "batteryVoltageWithUnit", value: "${batteryVoltage} V")

			BigDecimal batteryPercentage = 0
			BigDecimal batteryVoltageScaleMin = 2.1
			BigDecimal batteryVoltageScaleMax = 3.0

			if (batteryVoltage >= batteryVoltageScaleMin) {

				state.batteryOkay = true

				batteryPercentage = ((batteryVoltage - batteryVoltageScaleMin) / (batteryVoltageScaleMax - batteryVoltageScaleMin)) * 100.0
				batteryPercentage = batteryPercentage.setScale(0, BigDecimal.ROUND_HALF_UP)
				batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage

				def batteryLogLevel = batteryPercentage > 20 ? "info" : "warn"
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", $batteryLogLevel)

				sendEvent(name: "battery", value:batteryPercentage, unit: "%")
				sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
				sendEvent(name: "batteryState", value: "discharging")

			} else {

				// Very low voltages indicate an exhausted battery which requires replacement.

				state.batteryOkay = false

				batteryPercentage = 0

				logging("${device} : Battery : Exhausted battery requires replacement.", "warn")
				logging("${device} : Battery : $batteryPercentage% ($batteryVoltage V)", "warn")
				sendEvent(name: "battery", value:batteryPercentage, unit: "%")
				sendEvent(name: "batteryWithUnit", value:"${batteryPercentage} %")
				sendEvent(name: "batteryState", value: "exhausted")

			}

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "0400") {

		// Illuminance data received.

		if (receivedValue != null) {

			Integer lux = Integer.parseInt(receivedValue,16)
			Integer luxVariance = Math.abs(state.rawLux - lux)

			if (state.rawLux == null || luxVariance > luxTolerance) {

				state.rawLux = lux
				lux = lux > 0 ? Math.round(Math.pow(10,(lux/10000)) - 1) : 0

				def lastLux = device.currentState("illuminance").value
				lastLux = lastLux.indexOf('.') >= 0 ? 0 : lastLux.toInteger()  // In case a decimal has snuck through.
		
				String illuminanceDirection = lux > lastLux ? "brightening" : "darkening"
				String illuminanceDirectionLog = illuminanceDirection.capitalize()

				logging("${device} : Lux : ${illuminanceDirectionLog} from ${lastLux} to ${lux} lux.", "debug")
				sendEvent(name: "illuminance", value: lux, unit: "lux")
				sendEvent(name: "illuminanceDirection", value: "${illuminanceDirection}")
				sendEvent(name: "illuminanceWithUnit", value: "${lux} lux")

			} else {

				logging("${device} : Lux : Variance of ${luxVariance} (previously ${state.rawLux}, now ${lux}) is within tolerance.", "debug")

			}

		} else {

			logging("${device} : Lux : Illuminance message has been received without a value. This is weird.", "warn")

		}

	} else if (map.clusterId == "0001") {

		processConfigurationResponse(map)

	} else if (map.clusterId == "0003") {

		if (map.command == "01") {

			// Scrounge more value! We can capture a short press of the reset button and make it useful.
			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else {

			reportToDev(map)

		}

	} else if (map.clusterId == "0006") {

		logging("${device} : Skipped : Match Descriptor Request", "debug")

	} else if (map.clusterId == "0013") {

		logging("${device} : Skipped : Device Announce Broadcast", "debug")

	} else if (map.clusterId == "0400") {

		processConfigurationResponse(map)

	} else if (map.clusterId == "8004") {
		
		processDescriptors(map)

	} else if (map.clusterId == "8005") {

		logging("${device} : Skipped : Active End Point Response", "debug")

	} else if (map.clusterId == "8021") {

		logging("${device} : Skipped : Bind Response", "debug")

	} else {

		reportToDev(map)

	}

}
