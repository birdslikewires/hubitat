/*
 *
 *  Hive Motion Sensor Driver
 *
 */


@Field String driverVersion = "v1.01 (6th August 2026)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field
import hubitat.zigbee.clusters.iaszone.ZoneStatus

@Field int reportIntervalMinutes = 60
@Field int occupancyTimeoutSeconds = 40		// Device sends "active" motion roughly once per 30 seconds, but never sends a "clear".
@Field int deviceEndpoint = 6				// All of this device's clusters live on endpoint 6, not device.endpointId.
@Field String deviceName = "Hive Motion Sensor"
@Field BigDecimal temperatureInfoLogDelta = 0.5G


metadata {

	definition (name: "$deviceName", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/hive/drivers/hive_motion.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "MotionSensor"
		capability "Refresh"
		capability "TemperatureMeasurement"
		capability "VoltageMeasurement"

		attribute "batteryState", "string"
		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,0400,0402,0500", outClusters: "0019", manufacturer: "HiveHome.com", model: "MOT003", deviceJoinName: "$deviceName"

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

	device.name = "$deviceName"

	// The hub writes its own IEEE address to the IAS CIE attribute during interview, so all that's
	// left for us to do is answer the zone enrollment request. See parse() for the enrollResponse().

	requestBasic(deviceEndpoint)

	// Configure reporting.
	ArrayList<String> cmds = []
	cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 30, reportIntervalMinutes * 60, 50, [destEndpoint: deviceEndpoint])				// Temperature Measurement every ${reportIntervalMinutes} minutes
	cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 30, reportIntervalMinutes * 60, 0x01, [destEndpoint: deviceEndpoint])	// Battery Percentage Remaining every ${reportIntervalMinutes} minutes
	cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 30, reportIntervalMinutes * 60, 0x01, [destEndpoint: deviceEndpoint])	// Battery Voltage every ${reportIntervalMinutes} minutes
	sendZigbeeCommands(cmds)

}


void updateSpecifics() {
	// Called by library updated() method.

	return

}


void refresh() {

	logging("${device} : Refreshed", "info")

}


void processStatus(ZoneStatus status) {

	if (status.isAlarm1Set()) {

		unschedule(motionInactive)

		if (device.currentValue("motion") != "active") {
			sendEvent(name: "motion", value: "active")
			logging("${device} : Motion : Active", "info")
		}

		runIn(occupancyTimeoutSeconds, motionInactive)

	}

	if (status.isBatterySet()) {
		logging("${device} : Battery : Low battery flagged by device.", "warn")
	}

}


void motionInactive() {

	if (device.currentValue("motion") != "inactive") {
		sendEvent(name: "motion", value: "inactive")
		logging("${device} : Motion : Inactive", "info")
	}

}


void reportBatteryPercentage(String batteryPercentageHex) {

	int batteryPercentageRaw = zigbee.convertHexToInt(batteryPercentageHex)
	BigDecimal batteryPercentage = batteryPercentageRaw / 2

	batteryPercentage = batteryPercentage > 100 ? 100 : batteryPercentage
	batteryPercentage = batteryPercentage < 0 ? 0 : batteryPercentage

	logging("${device} : Battery : ${batteryPercentage}%", "info")

	if (batteryPercentage != device.currentValue("battery")) sendEvent(name: "battery", value: batteryPercentage, unit: "%")
	if (device.currentValue("batteryState") != "discharging") sendEvent(name: "batteryState", value: "discharging")

}


void reportBatteryVoltage(String batteryVoltageHex) {

	int batteryVoltageRaw = zigbee.convertHexToInt(batteryVoltageHex)
	BigDecimal batteryVoltage = batteryVoltageRaw / 10
	batteryVoltage = batteryVoltage.setScale(1, BigDecimal.ROUND_HALF_UP)

	logging("${device} : Battery : ${batteryVoltage} V", "debug")

	if (batteryVoltage != device.currentValue("voltage")) sendEvent(name: "voltage", value: batteryVoltage, unit: "V")

}


void parse(String description) {

	// Respond to the device first - checkDriver() can trigger a full reconfigure (Basic-cluster
	// read plus reporting binds), and we don't want that traffic queued ahead of a time-sensitive
	// response like the IAS Zone enrollResponse(). Health/version bookkeeping happens afterward.

	logging("${device} : parse() : $description", "trace")

	if (description.startsWith("zone status")) {

		ZoneStatus zoneStatus = zigbee.parseZoneStatus(description)
		processStatus(zoneStatus)

	} else if (description.startsWith("enroll request")) {

		logging("${device} : IAS Zone : Enrol request received, sending response.", "debug")
		sendZigbeeCommands(zigbee.enrollResponse(200, [destEndpoint: deviceEndpoint]))

	} else {

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

	updateHealthStatus()
	checkDriver()

}


void processMap(Map map) {

	if (map.cluster == "0001") {
		// Power Configuration Cluster

		if (map.attrId == "0021") {

			reportBatteryPercentage(map.value)

		} else if (map.attrId == "0020") {

			reportBatteryVoltage(map.value)

		} else {

			filterThis(map)

		}

	} else if (map.clusterId == "0020") {
		// Poll Control Cluster

		if (map.command == "00") {

			// Device Checkin - decline fast polling and let it return to its normal schedule.
			sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x${deviceEndpoint} 0x0020 0x00 {00 00 00}"])
			logging("${device} : Poll Control : Checkin acknowledged.", "debug")

		} else {

			filterThis(map)

		}

	} else if (map.cluster == "0402") {
		// Temperature Measurement Cluster

		if (map.attrId == "0000") {

			BigDecimal temperature = hexStrToSignedInt(map.value)
			temperature = temperature / 100
			temperature = temperature.setScale(1, BigDecimal.ROUND_HALF_UP)

			logging("${device} : Temperature : ${temperature} from hex value ${map.value}", "debug")

			String temperatureScale = location.temperatureScale
			if (temperatureScale == "F") {
				temperature = (temperature * 1.8) + 32
			}

			Object previousTemperature = device.currentValue("temperature")
			if (temperature != previousTemperature) {
				sendEvent(name: "temperature", value: temperature, unit: "${temperatureScale}")
			}
			if (hasSignificantDecimalChange(previousTemperature, temperature, temperatureInfoLogDelta)) {
				logging("${device} : Temperature : ${temperature} °${temperatureScale}", "info")
			}

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}

}
