/*
 * 
 *  Hive Thermostat Driver
 *	
 */


@Field String driverVersion = "v1.00 (1st March 2023)"

#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = true
@Field int reportIntervalMinutes = 10
@Field int checkEveryMinutes = 4


metadata {

	definition (name: "Hive Thermostat", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hive/drivers/hive_thermostat.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "PresenceSensor"
		capability "Refresh"

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


def testCommand() {

	logging("${device} : Test Command", "info")

	// THERMOSTAT_SYSTEM_CONFIG is an optional attribute. It we add other thermostats we need to determine if they support this and behave accordingly.
	// sendZigbeeCommands( zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_SYSTEM_CONFIG),
	// 		zigbee.readAttribute(FAN_CONTROL_CLUSTER, FAN_MODE_SEQUENCE),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, LOCAL_TEMPERATURE),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, COOLING_SETPOINT),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE),
	// 		zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_RUNNING_STATE),
	// 		zigbee.readAttribute(FAN_CONTROL_CLUSTER, FAN_MODE),
	// 		zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_ALARM_STATE) )

	//sendZigbeeCommands(zigbee.readAttribute(0x0201, 0x0400))
	
	//sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0000))

		// sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0000))	//Read LocalTemperature
		// sendZigbeeCommands(zigbee.readAttribute(0x201, 0x0012))	//Read OccupiedHeatingSetpoint
		// sendZigbeeCommands(zigbee.readAttribute(0x201, 0x001C))	//Read SystemMode
		// sendZigbeeCommands(zigbee.readAttribute(0x000, 0x0003))	//Read HW Version

	//sendZigbeeCommands(zigbee.configureReporting(CLUSTER_BATTERY_LEVEL, 0x0021, DataType.UINT8, 1, 10, 0x01))

	//sendZigbeeCommands(zigbee.configureReporting(0x0201, 0x001C, 0x30, 0, 60, null, [:], 500))

}


void installed() {

	// Runs after first installation.
	logging("${device} : Installed", "info")
	configure()

}


void configureSpecifics() {
	// Called by library configure() method.

	String modelCheck = "${getDeviceDataByName('model')}"
	device.name = "Hive Thermostat ${modelCheck}"

	// Configure reporting
	ArrayList<String> cmds = []
	cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 10, 60, 50)										// TemperatureMeasurement
	//cmds += zigbee.configureReporting(0x0000, 0x0021, DataType.UINT8, 600, 21600, 0x01)		// Battery
	cmds += zigbee.configureReporting(0x0000, 0x0021, DataType.UINT8, 10, 60, 0x01)			// Battery
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

	if (map.cluster == "0201") {
		// Thermostat Cluster

		// Ssssh. Apparantly, I'm not really a thermostat. :)
		return

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

