/*
 * 
 *  Xiaomi Aqara Temperature and Humidity Sensor WSDCGQ11LM Driver
 *	
 */


@Field String driverVersion = "v1.10 (12th October 2022)"


#include BirdsLikeWires.library
#include BirdsLikeWires.xiaomi
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 60
@Field int checkEveryMinutes = 10


metadata {

	definition (name: "Xiaomi Aqara Temperature and Humidity Sensor WSDCGQ11LM", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/xiaomi/drivers/xiaomi_aqara_temperature_humidity_sensor_wsdcgq11lm.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "PressureMeasurement"
		capability "PushableButton"
		capability "RelativeHumidityMeasurement"
		capability "Sensor"
		capability "TemperatureMeasurement"
		capability "VoltageMeasurement"

		attribute "absoluteHumidity", "number"
		attribute "pressureDirection", "string"
		//attribute "pressurePrevious", "string"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,FFFF,0402,0403,0405", outClusters: "0000,0004,FFFF", manufacturer: "LUMI", model: "lumi.weather", deviceJoinName: "WSDCGQ11LM", application: "05"

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

	updateDataValue("encoding", "Xiaomi")
	device.name = "Xiaomi Aqara Temperature and Humidity Sensor WSDCGQ11LM"
	sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)

}


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedValue = map.value

	if (map.cluster == "0402") { 

		// Received temperature data.

		String[] temperatureHex = receivedValue[2..3] + receivedValue[0..1]
		String temperatureFlippedHex = temperatureHex.join()
		BigDecimal temperature = hexStrToSignedInt(temperatureFlippedHex)
		temperature = temperature.setScale(2, BigDecimal.ROUND_HALF_UP) / 100

		logging("${device} : temperature : ${temperature} from hex value ${temperatureFlippedHex} flipped from ${map.value}", "trace")

		String temperatureScale = location.temperatureScale
		if (temperatureScale == "F") {
			temperature = (temperature * 1.8) + 32
		}

		if (temperature > 200 || temperature < -200) {

			logging("${device} : Temperature : Value of ${temperature}°${temperatureScale} is unusual. Watch out for batteries failing on this device.", "warn")

		} else {

			logging("${device} : Temperature : ${temperature} °${temperatureScale}", "info")
			sendEvent(name: "temperature", value: temperature, unit: "${temperatureScale}")

		}

	} else if (map.cluster == "0403") { 

		// Received pressure data.

		String[] pressureHex = receivedValue[2..3] + receivedValue[0..1]
		String pressureFlippedHex = pressureHex.join()
		BigDecimal pressure = hexStrToSignedInt(pressureFlippedHex)
		pressure = pressure.setScale(1, BigDecimal.ROUND_HALF_UP) / 10

		BigDecimal lastPressure = device.currentState("pressure") ? device.currentState("pressure").value.toBigDecimal() : 0

		////////// WORK TO DO - RECORD PREVIOUS PRESSURE AS LASTPRESSURE IF PRESSURE HAS CHANGED OR SOMETHING - TOO TIRED!

		// BigDecimal pressurePrevious = device.currentState("pressurePrevious").value.toBigDecimal()
		// if (pressurePrevious != null && pressure != lastPressure) {
		// 	endEvent(name: "pressurePrevious", value: lastPressure, unit: "kPa")
		// } else if 

		String pressureDirection = pressure > lastPressure ? "rising" : "falling"

		logging("${device} : pressure : ${pressure} from hex value ${pressureFlippedHex} flipped from ${map.value}", "trace")
		logging("${device} : Pressure : ${pressure} kPa", "info")
		sendEvent(name: "pressure", value: pressure, unit: "kPa")
		sendEvent(name: "pressureDirection", value: "${pressureDirection}")

	} else if (map.cluster == "0405") { 

		// Received humidity data.

		String[] humidityHex = receivedValue[2..3] + receivedValue[0..1]
		String humidityFlippedHex = humidityHex.join()
		BigDecimal humidity = hexStrToSignedInt(humidityFlippedHex)
		humidity = humidity.setScale(2, BigDecimal.ROUND_HALF_UP) / 100

		logging("${device} : humidity : ${humidity} from hex value ${humidityFlippedHex} flipped from ${map.value}", "trace")

		BigDecimal lastTemperature = device.currentState("temperature") ? device.currentState("temperature").value.toBigDecimal() : 0

		String temperatureScale = location.temperatureScale
		if (temperatureScale == "F") {
			lastTemperature = (lastTemperature - 32) / 1.8
		}

		BigDecimal numerator = (6.112 * Math.exp((17.67 * lastTemperature) / (lastTemperature + 243.5)) * humidity * 2.1674)
		BigDecimal denominator = lastTemperature + 273.15
		BigDecimal absoluteHumidity = numerator / denominator
        absoluteHumidity = absoluteHumidity.setScale(1, BigDecimal.ROUND_HALF_UP)

        String cubedChar = String.valueOf((char)(179))

		if (humidity > 100 || humidity < 0) {

			logging("${device} : Humidity : Value of ${humidity} is out of bounds. Watch out for batteries failing on this device.", "warn")

		} else {

			logging("${device} : Humidity (Relative) : ${humidity} %", "info")
			logging("${device} : Humidity (Absolute) : ${absoluteHumidity} g/m${cubedChar}", "info")
			sendEvent(name: "humidity", value: humidity, unit: "%")
			sendEvent(name: "absoluteHumidity", value: absoluteHumidity, unit: "g/m${cubedChar}")

		}

	} else if (map.cluster == "0000") {

		if (map.attrId == "0005") {

			// Scrounge more value! We can capture a short press of the reset button and make it useful.
			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else {

			// processBasic(map)
			reportToDev(map)

		}

	} else {

		reportToDev(map)

	}

}

