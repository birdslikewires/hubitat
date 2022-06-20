/*
 * 
 *  Hildebrand Glow MQTT Driver v1.02 (20th June 2022)
 *	
 */


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 1
@Field int occasionalUpdateMinutes = 10


metadata {

	definition (name: "Hildebrand Glow", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/hildebrand/drivers/glow_mqtt.groovy") {

		capability "PresenceSensor"
		capability "SignalStrength"

		command "disconnect"

		if (debugMode) {

			capability "Initialize"

			command "checkPresence"
			command "testCommand"

		}

	}

}


preferences {

	input name: "mqttBroker", type: "text", title: "MQTT Broker Address:", required: true
	input name: "mqttUser", type: "text", title: "MQTT Username:", required: false
	input name: "mqttPass", type: "password", title: "MQTT Password:", required: false
	//input name: "cloudId", type: "text", title: "Cloud ID:", required: false

	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false

}


void testCommand() {
	logging("${device} : Test Command", "info")
}


void installed() {
	// Runs after first installation.

	// Set default preferences.
	device.updateSetting("infoLogging", [value: "true", type: "bool"])
	device.updateSetting("debugLogging", [value: "${debugMode}", type: "bool"])
	device.updateSetting("traceLogging", [value: "${debugMode}", type: "bool"])

	// Set device name.
	device.name = "Hildebrand Glow"

	logging("${device} : Installed", "info")

	initialize()

}


void initialize() {

	int randomSixty
	String modelCheck = "${getDeviceDataByName('model')}"

	// Tidy up.
	unschedule()
	state.clear()
	state.occasionalUpdated = 0
	state.presenceUpdated = 0	
	sendEvent(name: "presence", value: "present", isStateChange: false)

	// Schedule presence checking.
	int checkEveryMinutes = 1
	randomSixty = Math.abs(new Random().nextInt() % 60)
	schedule("${randomSixty} 0/${checkEveryMinutes} * * * ? *", checkPresence)

	sendEvent(name: "initialisation", value: "complete", isStateChange: false)
	logging("${device} : Initialised", "info")

}


void configure() {

	// Create child devices.
	fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Electricity")
	fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Gas")

	// Notify.
	sendEvent(name: "configuration", value: "complete", isStateChange: false)
	logging("${device} : Configuration complete.", "info")
	
}


void updated() {
	// Runs when preferences are saved.

	disconnect()

	unschedule(infoLogOff)
	unschedule(debugLogOff)
	unschedule(traceLogOff)

	if (!debugMode) {
		runIn(2400,debugLogOff)
		runIn(1200,traceLogOff)
	}

	// In case cloud support is added, which I am presently too tired to be thinking about.
	if (settings?.cloudId) {
		state.mqttTopic = "SMART/HILD/${cloudId}"
		state.parsePasses = 1			// Cloud MQTT is received as 1 message and would need parsing in one pass.
	} else {
		state.mqttTopic = "glow/#"
		state.parsePasses = 3			// Local MQTT is received as 3 seperate messages (STATE plus two SENSORS), parsed sequentially.
	}

	state.parseCounter = 0				// We update some variables only every occasionalUpdateMinutes, so we must count our parse() passes.

	schedule("0/10 * * * * ? *", mqttConnect)

	logging("${device} : Preferences Updated", "info")

	loggingStatus()
	configure()

}


void disconnect() {
	// Disconnect from broker.

	interfaces.mqtt.disconnect()
	logging("${device} : MQTT : Disconnected from broker $mqttBroker (${state.mqttTopic}).", "info")

}


void uninstalled() {
	// Runs when device is removed.

	interfaces.mqtt.disconnect()
	logging("${device} : Uninstalled", "info")

}


void parse(String description) {

	// The parse is expected to run state.parsePasses number of times.
	Integer parseMax = state.parsePasses
	Integer parseCount = state.parseCounter + 1

	logging("${device} : Parse (Pass $parseCount/$parseMax) : $description", "trace")

	updatePresence()

	// Some values should only be updated every occasionalUpdateMinutes to reduce chatter and hub load.
	Boolean updateOccasional = false
	Integer updateCount = 0
	long occasionalUpdateMillis = occasionalUpdateMinutes * 60000
	long occasionalElapsedMillis = state.presenceUpdated - state.occasionalUpdated

	if (occasionalElapsedMillis > occasionalUpdateMillis || state.occasionalUpdated == 0) {

		updateOccasional = true
		logging("${device} : Parse : Threshold of $occasionalUpdateMinutes minutes reached. Updating occasional values!", "debug")

	}

	try {

		def msg = interfaces.mqtt.parseMessage(description)

		logging("${device} : Topic : ${msg.topic}", "trace")
		logging("${device} : Payload : ${msg.payload}", "trace")

		def json = new groovy.json.JsonSlurper().parseText(msg.payload)
		
		logging("${device} : JSON : ${json}", "debug")

		if (msg.topic.indexOf('STATE') >= 0) {

			sendEvent(name: "lqi", value: "${json.han.lqi}")
			sendEvent(name: "rssi", value: "${json.han.rssi}")

			if (updateOccasional) {

				updateDataValue("firmware", "${json.software}")
				updateDataValue("mac", "${json.ethmac}")
				updateDataValue("model", "${json.hardware}")
				updateDataValue("smetsversion", "${json.smetsversion}")
				updateDataValue("zigbee", "${json.zigbee}")

			}

		} else if (msg.topic.indexOf('electricitymeter') >= 0) {

			def childDevice = fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Electricity")

			BigDecimal cumulative = json.electricitymeter.energy.import.cumulative
			childDevice.parse([[name:"energy", value:cumulative]])

			BigDecimal power = json.electricitymeter.power.value * 1000
			power = power.intValue()
			childDevice.parse([[name:"power", value:power]])

			if (updateOccasional) {

				BigDecimal day = json.electricitymeter.energy.import.day
				BigDecimal week = json.electricitymeter.energy.import.week
				BigDecimal month = json.electricitymeter.energy.import.month

				childDevice.parse([[name:"day", value:day]])
				childDevice.parse([[name:"week", value:week]])
				childDevice.parse([[name:"month", value:month]])

				String mpan = json.electricitymeter.energy.import.mpan
				childDevice.setState([[name:"mpan", value:mpan]])

				String units = json.electricitymeter.energy.import.units
				childDevice.setState([[name:"energyUnit", value:"${units}"]])
				childDevice.setState([[name:"powerUnit", value:"W"]]) 			// This is a fixed value but just in case it's easier to find here.

				BigDecimal unitrate = json.electricitymeter.energy.import.price.unitrate
				BigDecimal unitratePence = unitrate * 100
				unitratePence = unitratePence.setScale(2, BigDecimal.ROUND_HALF_UP)
				childDevice.parse([[name:"unitrate", value:unitratePence]])
				childDevice.setState([[name:"unitrateUnits", value:"p/kWh"]])

				BigDecimal standingcharge = json.electricitymeter.energy.import.price.standingcharge
				BigDecimal standingchargePence = standingcharge * 100
				standingchargePence = standingchargePence.setScale(2, BigDecimal.ROUND_HALF_UP)
				childDevice.parse([[name:"standingcharge", value:standingchargePence]])
				childDevice.setState([[name:"standingchargeUnits", value:"p/day"]])

				String supplier = json.electricitymeter.energy.import.supplier
				childDevice.setState([[name:"supplier", value:supplier]])

			}

		} else if (msg.topic.indexOf('gasmeter') >= 0) {

			// Gas meters only send data every 30 minutes via the electricity meter, so there's no point being verbose.
			// It is expected that cumulative energy will soon be sent in cubic meters and the power value will be removed.

			if (updateOccasional) {

				def childDevice = fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Gas")

				BigDecimal cumulative = json.gasmeter.energy.import.cumulative
				childDevice.parse([[name:"energy", value:cumulative]])

				BigDecimal day = json.gasmeter.energy.import.day
				BigDecimal week = json.gasmeter.energy.import.week
				BigDecimal month = json.gasmeter.energy.import.month

				childDevice.parse([[name:"day", value:day]])
				childDevice.parse([[name:"week", value:week]])
				childDevice.parse([[name:"month", value:month]])

				String mprn = json.gasmeter.energy.import.mprn
				childDevice.setState([[name:"mprn", value:mprn]])

				String units = json.gasmeter.energy.import.units
				childDevice.setState([[name:"energyUnit", value:"${units}"]])

				BigDecimal unitrate = json.gasmeter.energy.import.price.unitrate
				BigDecimal unitratePence = unitrate * 100
				unitratePence = unitratePence.setScale(2, BigDecimal.ROUND_HALF_UP)
				childDevice.parse([[name:"unitrate", value:unitratePence]])
				childDevice.setState([[name:"unitrateUnits", value:"p/kWh"]])

				BigDecimal standingcharge = json.gasmeter.energy.import.price.standingcharge
				BigDecimal standingchargePence = standingcharge * 100
				standingchargePence = standingchargePence.setScale(2, BigDecimal.ROUND_HALF_UP)
				childDevice.parse([[name:"standingcharge", value:standingchargePence]])
				childDevice.setState([[name:"standingchargeUnits", value:"p/day"]])

				String supplier = json.gasmeter.energy.import.supplier
				childDevice.setState([[name:"supplier", value:supplier]])

			}

		}
					  
	} catch (Exception e) {

		logging("${device} : Parse : ${e.message}", "error")

	}

	if (parseCount == parseMax) {

		state.parseCounter = 0

		if (updateOccasional) {

			state.occasionalUpdated = state.presenceUpdated

		}

	} else {

		state.parseCounter = parseCount

	}

}

void mqttConnect() {

	try {

		def mqttInt = interfaces.mqtt

		if (mqttInt.isConnected()) {
			logging("${device} : MQTT : Connection to broker $mqttBroker (${state.mqttTopic}) is live.", "debug")
			return
		}

		String clientID = "hubitat-" + device.deviceNetworkId		
		mqttBrokerUrl = "tcp://" + settings?.mqttBroker + ":1883"
		mqttInt.connect(mqttBrokerUrl, clientID, settings?.mqttUser, settings?.mqttPass)
		pauseExecution(500)
		mqttInt.subscribe(state.mqttTopic)

	} catch (Exception e) {

		logging("${device} : MQTT : ${e.message}", "error")

	}

} 

void mqttClientStatus(String status) {

	if (status.indexOf('Connection succeeded') >= 0) {

		logging("${device} : MQTT : Connection to broker $mqttBroker (${state.mqttTopic}) is live.", "debug")

	} else {

		logging("${device} : MQTT : ${status}", "error")

	}

}
