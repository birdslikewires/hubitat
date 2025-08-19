/*
 * 
 *  Xiaomi Mijia Smart Light Sensor Driver
 *	
 */


@Field String driverVersion = "v1.17 (26th August 2023)"


#include BirdsLikeWires.library
#include BirdsLikeWires.xiaomi
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 60
@Field int checkEveryMinutes = 10
@Field int luxTolerance = 100


metadata {

	definition (name: "Xiaomi Mijia Smart Light Sensor", namespace: "BirdsLikeWires", author: "Andrew Davison",
		importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/xiaomi/drivers/xiaomi_mijia_smart_light_sensor.groovy") {

		capability "Battery"
		capability "Configuration"
		capability "IlluminanceMeasurement"
		capability "PushableButton"
		capability "Sensor"
		capability "VoltageMeasurement"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "LUMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "GZCGQ01LM"
		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "XIAOMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "GZCGQ01LM"
		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "LUMI", model: "lumi.sen_ill.agl01", deviceJoinName: "GZCGQ11LM"

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

	sendEvent(name: "illuminance", value: 0, unit: "lux", isStateChange: false)
	updateDataValue("encoding", "Zigbee")

	// Configure device reporting.
	// Honestly, I'd be amazed if the device is awake to accept these commands, but it doesn't hurt to ask.
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
 
	// Set device name.
	device.name = "Xiaomi Mijia Smart Light Sensor GZCGQ01LM"

	// Set device data.

	// Set initial lux state.
	state.rawLux = 0

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	return

}


void processMap(Map map) {

	if (map.cluster == "0001") { 

		if (map.attrId == "0020") {
			
			xiaomiDeviceStatus(map)

		} else {

			filterThis(map)

		}

	} else if (map.cluster == "0400") {

		// Illuminance data received.

		if (map.value != null) {

			Integer lux = Integer.parseInt(map.value,16)
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
				state.illuminanceDirection = illuminanceDirection

			} else {

				logging("${device} : Lux : Variance of ${luxVariance} (previously ${state.rawLux}, now ${lux}) is within tolerance.", "debug")

			}

		} else {

			logging("${device} : Lux : Illuminance message has been received without a value. This is weird.", "warn")

		}

	} else if (map.clusterId == "0003") {

		if (map.command == "01") {

			// Scrounge more value! We can capture a short press of the reset button and make it useful.
			logging("${device} : Trigger : Button Pressed", "info")
			sendEvent(name: "pushed", value: 1, isStateChange: true)

		} else {

			filterThis(map)

		}

	} else {

		filterThis(map)

	}

}
