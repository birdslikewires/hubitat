/*
 * 
 *  Hildebrand Glow MQTT Driver v1.01 (15th June 2022)
 *	
 */


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field String mqttTopic = "glow/#"
@Field int reportIntervalMinutes = 1


metadata {

	definition (name: "Hildebrand Glow", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hildebrand/drivers/glow_mqtt.groovy") {

		capability "Configuration"
		capability "PresenceSensor"
		capability "SignalStrength"

		command "disconnect"

		if (debugMode) {
			command "checkPresence"
			command "testCommand"
		}

	}

}


preferences {

	input name: "mqttBroker", type: "text", title: "MQTT Broker Address:", required: true
	input name: "username", type: "text", title: "MQTT Username:", required: false
	input name: "password", type: "password", title: "MQTT Password:", required: false

	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false

}


void testCommand() {
	logging("${device} : Test Command", "info")
}


void installed() {
	// Runs after first installation.

	logging("${device} : Installed", "info")

}


void configure() {

	int randomSixty
	String modelCheck = "${getDeviceDataByName('model')}"

	// Tidy up.
	unschedule()
	state.clear()
	state.presenceUpdated = 0	
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Set default preferences.
	device.updateSetting("infoLogging", [value: "true", type: "bool"])
	device.updateSetting("debugLogging", [value: "${debugMode}", type: "bool"])
	device.updateSetting("traceLogging", [value: "${debugMode}", type: "bool"])

	// Schedule presence checking.
	int checkEveryMinutes = 1
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	// Set device name.
	device.name = "Hildebrand Glow"

	// Create child devices.
	fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Electricity")
	fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Gas")

	// Notify.
	sendEvent(name: "configuration", value: "complete", isStateChange: false)
	logging("${device} : Configuration complete.", "info")
	
}


void updated() {
	// Runs when preferences are saved.

	interfaces.mqtt.disconnect()

	unschedule(infoLogOff)
	unschedule(debugLogOff)
	unschedule(traceLogOff)

	if (!debugMode) {
		runIn(2400,debugLogOff)
		runIn(1200,traceLogOff)
	}

	logging("${device} : Preferences Updated", "info")

	loggingStatus()
	configure()

	mqttConnect()
	schedule("0/10 * * * * ? *", mqttConnect)

}


void disconnect() {
	// Disconnect from broker.

	interfaces.mqtt.disconnect()
	logging("${device} : MQTT : Disconnected from $mqttBroker ($mqttTopic).", "info")

}


void uninstalled() {
	// Runs when device is removed.

	interfaces.mqtt.disconnect()
	logging("${device} : Uninstalled", "info")

}


void parse(String description) {

	logging("${device} : Parse : $description", "trace")

	updatePresence()

	try {

		def msg = interfaces.mqtt.parseMessage(description)

		logging("${device} : Topic : ${msg.topic}", "debug")
		logging("${device} : Payload : ${msg.payload}", "debug")

		def json = new groovy.json.JsonSlurper().parseText(msg.payload)
		
		logging("${device} : JSON : ${json}", "debug")

		if (msg.topic.indexOf('STATE') >= 0) {

			sendEvent(name: "lqi", value: "${json.han.lqi}")
			sendEvent(name: "rssi", value: "${json.han.rssi}")

			if (!state.updatedStates)

			updateDataValue("firmware", "${json.software}")
			updateDataValue("mac", "${json.ethmac}")
			updateDataValue("model", "${json.hardware}")
			updateDataValue("smetsversion", "${json.smetsversion}")
			updateDataValue("zigbee", "${json.zigbee}")

		} else if (msg.topic.indexOf('electricitymeter') >= 0) {

			String units = json.electricitymeter.energy.import.units

			String mpan = json.electricitymeter.energy.import.mpan
			String supplier = json.electricitymeter.energy.import.supplier

			BigDecimal unitrate = json.electricitymeter.energy.import.price.unitrate
			BigDecimal unitratePence = unitrate * 100
			unitratePence = unitratePence.setScale(2, BigDecimal.ROUND_HALF_UP)
			
			BigDecimal standingcharge = json.electricitymeter.energy.import.price.standingcharge
			BigDecimal standingchargePence = standingcharge * 100
			standingchargePence = standingchargePence.setScale(2, BigDecimal.ROUND_HALF_UP)

			BigDecimal day = json.electricitymeter.energy.import.day
			BigDecimal week = json.electricitymeter.energy.import.week
			BigDecimal month = json.electricitymeter.energy.import.month

			BigDecimal cumulative = json.electricitymeter.energy.import.cumulative
			BigDecimal power = json.electricitymeter.power.value * 1000
			power = power.intValue()

			def childDevice = fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Electricity")

			childDevice.parse([[name:"mpan", value:mpan]])
			childDevice.parse([[name:"supplier", value:supplier]])

			childDevice.parse([[name:"unitrate", value:unitrate]])
			childDevice.parse([[name:"standingcharge", value:standingcharge]])
			childDevice.parse([[name:"standingchargeWithUnit", value:"${standingchargePence} p/day"]])

			childDevice.parse([[name:"day", value:day]])
			childDevice.parse([[name:"week", value:week]])
			childDevice.parse([[name:"month", value:month]])

			childDevice.parse([[name:"energy", value:cumulative]])
			childDevice.parse([[name:"energyUnit", value:"${units}"]])
			childDevice.parse([[name:"power", value:power]])

		} else if (msg.topic.indexOf('gasmeter') >= 0) {

			String units = json.gasmeter.energy.import.units

			String mprn = json.gasmeter.energy.import.mprn
			String supplier = json.gasmeter.energy.import.supplier

			BigDecimal unitrate = json.gasmeter.energy.import.price.unitrate
			BigDecimal unitratePence = unitrate * 100
			unitratePence = unitratePence.setScale(2, BigDecimal.ROUND_HALF_UP)
			
			BigDecimal standingcharge = json.gasmeter.energy.import.price.standingcharge
			BigDecimal standingchargePence = standingcharge * 100
			standingchargePence = standingchargePence.setScale(2, BigDecimal.ROUND_HALF_UP)

			BigDecimal day = json.gasmeter.energy.import.day
			BigDecimal week = json.gasmeter.energy.import.week
			BigDecimal month = json.gasmeter.energy.import.month

			BigDecimal cumulative = json.gasmeter.energy.import.cumulative

			def childDevice = fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Gas")

			childDevice.parse([[name:"mprn", value:mprn]])
			childDevice.parse([[name:"supplier", value:supplier]])

			childDevice.parse([[name:"unitrate", value:unitrate]])
			//childDevice.parse([[name:"unitrateWithUnit", value:"${unitratePence} p/kWh"]])
			childDevice.parse([[name:"standingcharge", value:standingcharge]])
			//childDevice.parse([[name:"standingchargeWithUnit", value:"${standingchargePence} p/day"]])

			childDevice.parse([[name:"day", value:day]])
			//childDevice.parse([[name:"dayWithUnit", value:"${day} ${units}"]])
			childDevice.parse([[name:"week", value:week]])
			//childDevice.parse([[name:"weekWithUnit", value:"${week} ${units}"]])
			childDevice.parse([[name:"month", value:month]])
			//childDevice.parse([[name:"monthWithUnit", value:"${month} ${units}"]])

			childDevice.parse([[name:"energy", value:cumulative]])
			//childDevice.parse([[name:"energyWithUnit", value:"${cumulative} ${units}"]])
			childDevice.parse([[name:"power", value:power]])
			//childDevice.parse([[name:"powerWithUnit", value:"${power} W"]])

		}
					  
	} catch (Exception e) {

		logging("${device} : Parse : ${e.message}", "error")

	}

}

void mqttConnect() {

	try {

		def mqttInt = interfaces.mqtt

		if (mqttInt.isConnected()) {
			logging("${device} : MQTT : Connection to $mqttBroker ($mqttTopic) is live.", "debug")
			return
		}

		String clientID = "hubitat-" + device.deviceNetworkId		
		mqttBrokerUrl = "tcp://" + settings?.mqttBroker + ":1883"
		mqttInt.connect(mqttBrokerUrl, clientID, settings?.username, settings?.password)
		pauseExecution(500)
		mqttInt.subscribe(mqttTopic)

	} catch (Exception e) {

		logging("${device} : MQTT : ${e.message}", "error")

	}

} 

void mqttClientStatus(String status) {

	if (status.indexOf('Connection succeeded') >= 0) {

		logging("${device} : MQTT : Connection established to $mqttBroker ($mqttTopic).", "info")

	} else {

		logging("${device} : MQTT : ${status}", "error")

	}

}
