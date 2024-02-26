/*
 * 
 *  Sonoff TRVZB Driver
 *	
 */


@Field String driverVersion = "v0.01 (7th December 2023)"
@Field boolean debugMode = true


#include BirdsLikeWires.library
import groovy.transform.Field

@Field String deviceName = "Sonoff Thermostatic Radiator Valve"
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/sonoff/drivers/sonoff_trvzb.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "TemperatureMeasurement"
		capability "ThermostatSetpoint"
		capability "VoltageMeasurement"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0006,0020,0201,FC57,FC11", outClusters: "000A,0019", manufacturer: "SONOFF", model: "TRVZB", deviceJoinName: "Sonoff Thermostatic Radiator Valve"

	}

}


preferences {
	
	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
	
}


void testCommand() {

	logging("${device} : Test Command", "info")

	zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)

}


void configureSpecifics() {
	// Called by general configure() method

	String modelCheck = "${getDeviceDataByName('model')}"
	device.name = "$deviceName"

	sendZigbeeCommands(zigbee.configureReporting(0x0201, 0x0000, 0x29, 1, 60))							// (0x0201, 0x0000) Temperature Reporting

}


void updateSpecifics() {
	// Called by library updated() method

	return

}


void parse(String description) {

	updateHealthStatus()
	checkDriver()

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

	if (map.cluster == "0201") {
		// Thermostat Cluster

		if (map.attrId == "0000" || map.attrId == "0012") {
			// temperature or heatingSetpoint

			String temperatureType = ("${map.attrId}" == "0000") ? "temperature" : "thermostatSetpoint"

			BigDecimal temperature = hexStrToSignedInt(map.value)
			temperature = temperature / 100
			temperature = temperature.setScale(1, BigDecimal.ROUND_DOWN)

			logging("${device} : ${temperatureType} : ${temperature} from hex value ${map.value} ", "debug")

			String temperatureScale = location.temperatureScale
			if (temperatureScale == "F") {
				temperature = (temperature * 1.8) + 32
			}

			logging("${device} : ${temperatureType} : ${temperature} °${temperatureScale}", "info")
			sendEvent(name: "${temperatureType}", value: temperature, unit: "${temperatureScale}")

			// if (temperatureType == "heatingSetpoint") {

			// 	// System is heating-only. The cooling setpoint can only ever be our heating target.
			// 	sendEvent(name: "thermostatSetpoint", value: temperature, unit: "${temperatureScale}")

			// 	// Now we need to check whether this was a scheduled or manual setpoint change.
			// 	//getSystemMode()
			// 	//getTemperatureSetpointHoldDuration()

			// }
		
		} else if (map.attrId == "001C") {
			// Thermostat Mode

			// It's likely this will always be "04" for "heating" as this is a heating-only device.
			// I suppose it's possible it could be "00" for "off". Maybe.
			logging("${device} : Thermostat Mode : hex value ${map.value} ", "debug")

		
		} else {

			filterThis(map)

		}

	} else if (map.cluster == "FC11") {
		// Vendor Specific Cluster

		if (map.attrId == "6007") {
			// valve_motor_running_voltage

			/// Just quickly threw this in to see what we get overnight, or whenever I get back to this.

			BigDecimal motorVoltage = hexStrToSignedInt(map.value)
			motorVoltage = motorVoltage / 100
			motorVoltage = motorVoltage.setScale(1, BigDecimal.ROUND_DOWN)  

			logging("${device} : valve_motor_running_voltage : ${motorVoltage} from hex value ${map.value} ", "debug")

		}


	} else {

		filterThis(map)

	}
	
}
