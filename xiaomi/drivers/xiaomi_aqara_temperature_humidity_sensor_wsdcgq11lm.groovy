/*
 * 
 *  Xiaomi Aqara Temperature and Humidity Sensor WSDCGQ11LM Driver
 *	
 */


@Field String driverVersion = "v1.11 (12th October 2022)"


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
		capability "RelativeHumidityMeasurement"
		capability "Sensor"
		capability "TemperatureMeasurement"
		capability "VoltageMeasurement"
		capability "PushableButton"

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


void processTemperature(temperatureFlippedHex) {

    BigDecimal temperature = hexStrToSignedInt(temperatureFlippedHex)
    temperature = temperature.setScale(2, BigDecimal.ROUND_HALF_UP) / 100

    logging("${device} : temperature : ${temperature} from hex value ${temperatureFlippedHex}", "trace")

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
}


void processPressure(pressureFlippedHex) {

    BigDecimal pressure = hexStrToSignedInt(pressureFlippedHex)
    pressure = pressure.setScale(1, BigDecimal.ROUND_HALF_UP) / 10

    BigDecimal lastPressure = device.currentState("pressure") ? device.currentState("pressure").value.toBigDecimal() : 0

    ////////// WORK TO DO - RECORD PREVIOUS PRESSURE AS LASTPRESSURE IF PRESSURE HAS CHANGED OR SOMETHING - TOO TIRED!

    // BigDecimal pressurePrevious = device.currentState("pressurePrevious").value.toBigDecimal()
    // if (pressurePrevious != null && pressure != lastPressure) {
    // 	endEvent(name: "pressurePrevious", value: lastPressure, unit: "kPa")
    // } else if 

    String pressureDirection = pressure > lastPressure ? "rising" : "falling"

    logging("${device} : pressure : ${pressure} from hex value ${pressureFlippedHex}", "trace")
    logging("${device} : Pressure : ${pressure} kPa", "info")
    sendEvent(name: "pressure", value: pressure, unit: "kPa")
    sendEvent(name: "pressureDirection", value: "${pressureDirection}")
}


void processHumidity(humidityFlippedHex) {

    BigDecimal humidity = hexStrToSignedInt(humidityFlippedHex)
    humidity = humidity.setScale(2, BigDecimal.ROUND_HALF_UP) / 100

    logging("${device} : humidity : ${humidity} from hex value ${humidityFlippedHex}", "trace")

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
}


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	String[] receivedValue = map.value

	if (map.cluster == "0402") { 

		// Received temperature data.
        String[] temperatureHex = receivedValue[2..3] + receivedValue[0..1]
        String temperatureFlippedHex = temperatureHex.join()
        logging("${device} : processMap() : temperature ${temperatureFlippedHex}", "trace")
        processTemperature(temperatureFlippedHex)

	} else if (map.cluster == "0403") { 

		// Received pressure data.
        String[] pressureHex = receivedValue[2..3] + receivedValue[0..1]
        String pressureFlippedHex = pressureHex.join()
        logging("${device} : processMap() : pressure ${pressureFlippedHex}", "trace")
        processPressure(pressureFlippedHex)

	} else if (map.cluster == "0405") { 

		// Received humidity data.
        String[] humidityHex = receivedValue[2..3] + receivedValue[0..1]
        String humidityFlippedHex = humidityHex.join()
        logging("${device} : processMap() : humidity ${humidityFlippedHex}", "trace")
        processHumidity(humidityFlippedHex)
        
	} else if (map.cluster == "0000") {

		if (map.attrId == "0005") {

			// Scrounge more value! We can capture a short press of the reset button and make it useful.
			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else {

			// processBasic(map)
			filterThis(map)

		}

	} else {

		filterThis(map)

	}

}


// TODO attribution to veeceeoh's driver + github ref
// Reverses order of bytes in hex string
def reverseHexString(hexString) {
	def reversed = ""
	for (int i = hexString.length(); i > 0; i -= 2) {
		reversed += hexString.substring(i - 2, i )
	}
	return reversed
}


// TODO attribution to veeceeoh's driver + github ref
// Parse checkin message from lumi.weather device (WSDCGQ11LM) which contains
// a full set of sensor readings.
def parseCheckinMessageSpecifics(hexString) {
	logging("Received check-in message","debug")
	def result
	// First byte of hexString is UINT8 of payload length in bytes, so it is skipped
	def strPosition = 2
	def strLength = hexString.size() - 2
	while (strPosition < strLength) {
		def dataTag = Integer.parseInt(hexString[strPosition++..strPosition++], 16)  // Each attribute of the check-in message payload is preceded by a unique 1-byte tag value
		def dataType = Integer.parseInt(hexString[strPosition++..strPosition++], 16)  // After each attribute tag, the following byte gives the data type of the attribute data
		def dataLength = DataType.getLength(dataType)  // This looks up the length of data for the determined data type
		def dataPayload  // This is used to collect the payload data of each check-in message attribute
		if (dataLength == null || dataLength == -1 || dataLength == 0) {  // A length of null or -1 means the data type is probably variable-length, and 0 length is invalid
			logging("Check-in message contains unsupported dataType 0x${Integer.toHexString(dataType)} for dataTag 0x${Integer.toHexString(dataTag)} with dataLength $dataLength","debug")
			return
		} else {
			if (strPosition > (strLength - dataLength)) {
				logging("Ran out of data before finishing parse of check-in message","debug")
				return
			}
			dataPayload = hexString[strPosition++..(strPosition+=(dataLength * 2) - 1)-1]  // Collect attribute tag payload according to data length of its data type
			dataPayload = reverseHexString(dataPayload)  // Reverse order of bytes for big endian payload
			def dataDebug1 = "Check-in message: Found dataTag 0x${Integer.toHexString(dataTag)}"
			def dataDebug2 = "dataType 0x${Integer.toHexString(dataType)}, dataLength $dataLength, dataPayload $dataPayload"
			switch (dataTag) {
				case 0x01:  // Battery voltage
					logging("$dataDebug1 (battery), $dataDebug2","debug")
                	//reportBattery(dataPayload, 1000, 2.8, 3.0) // already done in parent call xiaomiDeviceStatus()
					break
				case 0x05:  // RSSI dB
					def convertedPayload = Integer.parseInt(dataPayload,16)
					logging("$dataDebug1 (RSSI dB), $dataDebug2 ($convertedPayload)","debug")
					state.RSSI = convertedPayload
					break
				case 0x06:  // LQI
					def convertedPayload = Integer.parseInt(dataPayload,16)
					logging("$dataDebug1 (LQI), $dataDebug2 ($convertedPayload)","debug")
					state.LQI = convertedPayload
					break
				case 0x64:  // Temperature in Celcius
					logging("$dataDebug1 (temperature), $dataDebug2","debug")
					processTemperature(dataPayload)
					break
				case 0x65:  // Relative humidity
					logging("$dataDebug1 (humidity), $dataDebug2","debug")
					processHumidity(dataPayload)
					break
				case 0x66:  // Atmospheric pressure
					logging("$dataDebug1 (pressure), $dataDebug2","debug")
					processPressure(dataPayload)
					break
				case 0x0A:  // ZigBee parent DNI (device network identifier)
					logging("$dataDebug1 (ZigBee parent DNI), $dataDebug2","debug")
					state.zigbeeParentDNI = dataPayload
					break
				default:
					logging("$dataDebug1 (unknown), $dataDebug2","debug")
			}
		}
	}
}

