/*
 * 
 *  Samotech SM323 Dimmer Module Driver
 *	
 */


@Field String driverVersion = "v1.09 (29th April 2026)"
@Field boolean debugMode = false

#include BirdsLikeWires.library
import groovy.transform.Field

@Field int reportIntervalMinutes = 1
@Field String deviceMan = "Samotech"
@Field String deviceType = "Dimmer Module"
@Field Integer levelInfoLogDelta = 5


metadata {

	definition (name: "$deviceMan $deviceType SM323", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/samotech/drivers/samotech_dimmer_module_sm323.groovy") {

		capability "Actuator"
		capability "Configuration"
		capability "HealthCheck"
		capability "Light"
		capability "PowerMeter"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0702,0B04,0B05,1000", outClusters: "0019", manufacturer: "Samotech", model: "SM323", deviceJoinName: "$deviceMan $deviceType SM323"

	}

}


preferences {
	
	input name: "electricalMeasure", type: "bool", title: "Enable electrical measurement", description: "Requests power reporting. This will not work properly if the module is wired without neutral.", defaultValue: false
	input name: "levelMax", type: "number", title: "Maximum level", description: "Set the maximum brightness level for the dimmer (1-100%).", range: "1..100", defaultValue: 100

	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false	

}


void testCommand() {

	logging("${device} : Test Command", "info")

}


void configureSpecifics() {

	requestBasic()

	String deviceModel = getDeviceDataByName('model')
	device.name = "$deviceMan $deviceType $deviceModel"

	// Reporting
	int reportIntervalSeconds = reportIntervalMinutes * 60

	ArrayList<String> cmds = zigbee.writeAttribute(0x0006, 0x4003, 0x30, 0xFF)	// If power is lost, return to previous state when re-energised.
	cmds += zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, reportIntervalSeconds, 0x00)
	cmds += zigbee.configureReporting(0x0008, 0x0000, 0x20, 0, reportIntervalSeconds, 0x01)
	sendZigbeeCommands(cmds)

	if (settings.electricalMeasure) {
		cmds = zigbee.configureReporting(0x0B04, 0x050B, 0x29, 0, reportIntervalSeconds, 0x01)
		sendZigbeeCommands(cmds)
	}

}


void updateSpecifics() {

	return

}


void ping() {

	logging("${device} : Ping", "info")
	refresh()

}


void refresh() {

	logging("${device} : Refreshing", "debug")
	sendZigbeeCommands([
		"he rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x0000 {}",	// state
		"he rattr 0x${device.deviceNetworkId} 0x01 0x0008 0x0000 {}"	// level
	])

}


void off() {

	logging("${device} : off()", "debug")
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"])

}


void on() {

	logging("${device} : on()", "debug")
	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}"])

}


void setLevel(BigDecimal level) {

	setLevel(level,1)

}


void setLevel(BigDecimal level, BigDecimal duration) {

	// Calculate the allowable level using the levelMax preference.
	Integer maxLevelPercent = (settings.levelMax as Integer) ?: 100
	BigDecimal effectivePercentage = (level / 100.0) * maxLevelPercent
	int finalDeviceLevel = Math.round(effectivePercentage).intValue()
	String hexLevel = percentageToHex(finalDeviceLevel)

	BigDecimal safeDuration = duration <= 25 ? (duration*10) : 255
	String hexDuration = Integer.toHexString(safeDuration.intValue()).padLeft(2,'0')

	String pluralisor = duration == 1 ? "" : "s"
	logging("${device} : setLevel : Got level request of '${level}' (${finalDeviceLevel}%) [${hexLevel}] over '${duration}' (${safeDuration} decisecond${pluralisor}) [${hexDuration}].", "debug")

	sendZigbeeCommands(["he cmd 0x${device.deviceNetworkId} 0x01 0x0008 0x04 {${hexLevel} ${hexDuration} 00}"])

}


void parse(String description) {

	updateHealthStatus()
	checkDriver()

	Map descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {

		logging("${device} : Parse : ${descriptionMap}", "debug")
		processMap(descriptionMap)

	} else {
		
		logging("${device} : Parse : Failed to parse received data. Please report these messages to the developer.", "warn")
		logging("${device} : Parse : ${description}", "error")

	}

}


void processMap(Map map) {

	logging("${device} : processMap() : ${map}", "trace")

	if (map.cluster == "0006" || map.clusterId == "0006") {

		if (map.command == "01" || map.command == "0A") {
			// Relay States

			boolean isDigital = state.lastDigitalCommand && (now() - (state.lastDigitalCommand as Long)) < 500
			String eventType = isDigital ? "digital" : "physical"
			String previousSwitch = "${state.lastReportedSwitchState ?: device.currentValue("switch") ?: ""}"

			if (map.value == "00") {

				if (previousSwitch != "off") {
					sendEvent(name: "switch", value: "off", type: eventType)
					logging("${device} : Switch : Off", "info")
				}
				state.lastReportedSwitchState = "off"

			} else {

				if (previousSwitch != "on") {
					sendEvent(name: "switch", value: "on", type: eventType)
					logging("${device} : Switch : On", "info")
				}
				state.lastReportedSwitchState = "on"

			}

		} else if (map.command == "07") {

			processConfigurationResponse(map)

		} else if (map.command == "0B") {
			// Default Response - device is ACKing a command sent from Hubitat, so next state change is digital

			state.lastDigitalCommand = now()
			logging("${device} : Digital Command : Acknowledged by device.", "debug")

		} else if (map.command == "00") {

			logging("${device} : Skipped : State Counter Message", "debug")

		} else {

			filterThis(map)

		}

	} else if (map.cluster == "0008" || map.clusterId == "0008") {
		// Level

		if (map.command == "01" || map.command == "0A") {
			// Reading

			Integer maxLevelPercent = (settings.levelMax as Integer) ?: 100
			int currentLevel = hexToPercentage("${map.value}")
			long scaledPercentage = Math.round((currentLevel / maxLevelPercent.toDouble()) * 100.0)
			scaledPercentage = Math.min(100L, Math.max(0L, scaledPercentage))
			scaledPercentage = scaledPercentage >= 98 ? 100 : scaledPercentage

			if (currentLevel > maxLevelPercent) {
				logging("${device} : Over Level : Reported ${currentLevel} is over the configured maximum of ${maxLevelPercent}. Correcting!", "warn")
				setLevel(100,0)
			}

			Object previousLevel = device.currentValue("level")
			if (scaledPercentage != previousLevel) sendEvent(name: "level", value: "${scaledPercentage}")
			if (hasSignificantIntegerChange(previousLevel, scaledPercentage, levelInfoLogDelta)) {
				logging("${device} : Level : ${scaledPercentage}", "info")
			}
			logging("${device} : Real Level : ${currentLevel}", "debug")

		} else if (map.command == "0B") {
			// Status

			logging("${device} : Fading", "debug")

		} else {

			filterThis(map)

		}

	} else if (map.cluster == "0B04") {
		// Electrical Measurement

		if (settings.electricalMeasure) {

			if (map.attrId == "050B") {
				// Instantaneous power reading. 
				int powerValue = Math.round(zigbee.convertHexToInt(map.value) / 10.0) as int
				sendEvent(name: "power", value: powerValue, unit: "W")
			}

		} else {

			return

		}

	} else {

		filterThis(map)

	}
	
}
