/*
 * 
 *  Xiaomi Aqara Cube Controller Driver
 *	
 */


@Field String driverVersion = "v1.04 (26th August 2023)"


#include BirdsLikeWires.library
#include BirdsLikeWires.xiaomi
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 60
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "Xiaomi Aqara Cube Controller", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_aqara_cube_controller.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PushableButton"
		capability "VoltageMeasurement"

		attribute "batteryState", "string"
		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,0019,0012", outClusters: "0000,0004,0003,0005,0019,0012", manufacturer: "LUMI", model: "lumi.sensor_cube", deviceJoinName: "MFKZQ01LM", application: "05"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")

}


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.xiaomi
	
	updateDataValue("encoding", "xiaomi")

	device.name = "Xiaomi Aqara Cube Controller MFKZQ01LM"

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void push(buttonId) {
	
	sendEvent(name:"pushed", value: buttonId, isStateChange:true)
	
}


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String receivedValue = flipLittleEndian(map,"value")

	logging("${device} : Value '${receivedValue}' flipped from '${map.value}'.", "debug")

	if (map.cluster == "000C") {

		// Rotation
		logging("${device} : Rotation", "info")

	} else if (map.cluster == "0012") {

		// Knock / Flip / Slide / Shake

		String motionType = receivedValue[0..1]
		String binaryValue = hexToBinary(receivedValue[2..3])
		Integer sourceFace = Integer.parseInt(binaryValue[2..4],2)
		Integer targetFace = Integer.parseInt(binaryValue[5..7],2)

		logging("${device} : Motion : motionType: $motionType, binaryValue: $binaryValue, sourceFace: $sourceFace, targetFace: $targetFace", "debug")

		if (motionType == "00") {

			switch(binaryValue[0..1]) {

				case "00":
		
					if (targetFace == 0) {

						logging("${device} : Shaken", "info")

					} else if (targetFace == 2) {

						logging("${device} : Woken", "info")

					} else {

						filterThis(map)

					}

					break

				case "01":

					logging("${device} : Flipped 90 from ${sourceFace} to ${targetFace}", "info")

					break

				case "10":

					// Annoying - a 180 flip always reports a sourceFace of 0, though we can work it out given the targetFace.

					logging("${device} : Flipped 180 from ${sourceFace} to ${targetFace}", "info")

					break

			}
		} else if (motionType == "01") {

			logging("${device} : Slid on ${targetFace}", "info")

		} else if (motionType == "02") {

			logging("${device} : Knocked on ${targetFace}", "info")

		}

	} else if (map.clusterId == "8004") {
		
		processDescriptors(map)

	} else if (map.cluster == "0000") { 

		if (map.attrId == "0005") {

			// The reset button is inaccessible when the Cube is clipped together, so we'll not send an event on this device.
			logging("${device} : Reset Tapped", "info")

		} else if (map.attrId == "FF01") {

			def deviceData = ""
			def dataSize = map.value.size()

			if (dataSize > 20) {
				deviceData = map.value
			} else {
				logging("${device} : deviceData : No battery information in this report.", "debug")
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

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}

}
