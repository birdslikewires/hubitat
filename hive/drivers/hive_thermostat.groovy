/*
 * 
 *  Hive Thermostat Driver
 *	
 */


@Field String driverVersion = "v0.51 (6th March 2023)"

#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "Hive Thermostat", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hive/drivers/hive_thermostat.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "Refresh"
		capability "TemperatureMeasurement"
		capability "VoltageMeasurement"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,0009,000A,0201,FD00", outClusters: "000A,0402,0019", manufacturer: "Computime", model: "SLR2", deviceJoinName: "Computime Boiler Controller SLR2"

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
	// Called by library configure() method.

	String modelCheck = "${getDeviceDataByName('model')}"
	device.name = "Hive Thermostat ${modelCheck}"

	// Configure reporting
	ArrayList<String> cmds = []
	cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 10, 60, 50)						// TemperatureMeasurement every minute
	cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 10, 600, 0x01)		// Battery (DataType.UINT8 = 0x20) every ten minutes
	sendZigbeeCommands(cmds)  

}


void updateSpecifics() {
	// Called by library updated() method.

	return

}


void refresh() {

	logging("${device} : Refreshed", "info")

}


void parse(String description) {

	updatePresence()

	logging("${device} : parse() : $description", "trace")

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		try {

			processMap(descriptionMap)

		} catch (Exception e) {

			// Slice-and-dice the string we receive.
			descriptionMap = description.split(', ').collectEntries {
				entry -> def pair = entry.split(': ')
				[(pair.first()): pair.last()]
			}

			try {

				processMap(descriptionMap)

			} catch (Exception ee) {

				reportToDev(descriptionMap)

			}

		}

	} else {
		
		reportToDev(descriptionMap)

	}

}


void processMap(Map map) {

	if (map.cluster == "0001") {
		// Power Configuration Cluster

		reportBattery("${map.value}", 10, 4.8, 6.0)

	} else if (map.cluster == "0402") {
		// Temperature Measurement Cluster

			if (map.attrId == "0000") {
			// Temperature

			BigDecimal temperature = hexStrToSignedInt(map.value)
			temperature = temperature / 100
			temperature = temperature.setScale(1, BigDecimal.ROUND_DOWN)  // They seem to round down for the stat display.

			logging("${device} : Temperature : ${temperature} from hex value ${map.value} ", "debug")

			String temperatureScale = location.temperatureScale
			if (temperatureScale == "F") {
				temperature = (temperature * 1.8) + 32
			}

			logging("${device} : temperature : ${temperature} °${temperatureScale}", "info")
			sendEvent(name: "temperature", value: temperature, unit: "${temperatureScale}")

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}
	
}
