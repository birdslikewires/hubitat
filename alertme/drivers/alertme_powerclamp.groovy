/*
 * 
 *  AlertMe Power Clamp Driver v1.21 (24th September 2022)
 *	
 */


#include BirdsLikeWires.alertme
#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 2
@Field int checkEveryMinutes = 1
@Field int rangeEveryHours = 6


metadata {

	definition (name: "AlertMe Power Clamp", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_powerclamp.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "EnergyMeter"
		capability "PowerMeter"
		capability "PresenceSensor"
		capability "Refresh"
		capability "SignalStrength"
		capability "TamperAlert"
		capability "TemperatureMeasurement"

		command "normalMode"
		command "rangingMode"
		//command "quietMode"

		attribute "batteryState", "string"
		attribute "batteryVoltage", "string"
		attribute "mode", "string"
		attribute "uptime", "string"
		attribute "uptimeReadable", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00EF", outClusters: "", manufacturer: "AlertMe.com", model: "Power Clamp", deviceJoinName: "AlertMe Power Clamp"
		fingerprint profileId: "C216", inClusters: "00F0,00F3,00EF", outClusters: "", manufacturer: "AlertMe.com", model: "Power Clamp Device", deviceJoinName: "AlertMe Power Clamp"

	}

}


preferences {
	
	input name: "sensorCorrection", type: "decimal", title: "Sensor Correction Multiplier", defaultValue: 1.00

	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F6 {11 00 FC 01} {0xC216}"])	   // version information request

}


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.alertme

	// Set device name.
	device.name = "AlertMe Power Clamp"

	// Enable power control.
	sendZigbeeCommands(["he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00EE {11 00 01 01} {0xC216}"])

}


void processMap(Map map) {

	if (map.clusterId == "00EF") {

		// Power and energy messages.

		BigDecimal sensorCorrectionMultiplier = sensorCorrection.toBigDecimal()

		if (map.command == "81") {

			// Power Reading

			def powerValueHex = "undefined"
			BigDecimal powerValue = 0

			// These power readings are so frequent that we only log them in debug or trace.
			powerValueHex = map.data[0..1].reverse().join()
			logging("${device} : power byte flipped : ${powerValueHex}", "trace")
			powerValue = zigbee.convertHexToInt(powerValueHex)
			logging("${device} : power sensor reports : ${powerValue}", "debug")

			powerValue = powerValue * sensorCorrectionMultiplier
			powerValue = powerValue.setScale(0, BigDecimal.ROUND_HALF_UP)

			sendEvent(name: "power", value: powerValue, unit: "W")
			logging("${device} : Power : ${powerValue} W", "info")

		} else if (map.command == "82") {

			// Command 82 returns energy summary in watt-hours with an uptime counter.

			// Energy

			String energyValueHex = "undefined"
			energyValueHex = map.data[0..3].reverse().join()
			logging("${device} : energy byte flipped : ${energyValueHex}", "trace")

			BigInteger energyValue = new BigInteger(energyValueHex, 16)
			logging("${device} : energy counter reports : ${energyValue}", "debug")

			BigDecimal energyValueDecimal = BigDecimal.valueOf(energyValue / 3600 / 1000) * sensorCorrection
			energyValueDecimal = energyValueDecimal.setScale(4, BigDecimal.ROUND_HALF_UP)

			logging("${device} : Energy : ${energyValueDecimal} kWh", "info")

			sendEvent(name: "energy", value: energyValueDecimal, unit: "kWh")

			// Uptime

			String uptimeValueHex = "undefined"
			uptimeValueHex = map.data[4..8].reverse().join()
			logging("${device} : uptime byte flipped : ${uptimeValueHex}", "trace")

			BigInteger uptimeValue = new BigInteger(uptimeValueHex, 16)
			logging("${device} : uptime counter reports : ${uptimeValue}", "debug")

			def newDhmsUptime = []
			newDhmsUptime = millisToDhms(uptimeValue * 1000)
			String uptimeReadable = "${newDhmsUptime[3]}d ${newDhmsUptime[2]}h ${newDhmsUptime[1]}m"

			logging("${device} : Uptime : ${uptimeReadable}", "debug")

			sendEvent(name: "uptime", value: uptimeValue, unit: "s")
			sendEvent(name: "uptimeReadable", value: uptimeReadable)

		} else {

			reportToDev(map)

		}

	} else {

		reportToDev(map)

	}

}
