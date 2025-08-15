/*
 * 
 *  Hildebrand Glow MQTT Driver
 *	
 */


@Field String driverVersion = "v1.11 (26th August 2023)"


#include BirdsLikeWires.library
import groovy.transform.Field

@Field boolean debugMode = false
@Field int reportIntervalMinutes = 1
@Field int checkEveryMinutes = 1
@Field int occasionalUpdateMinutes = 10


metadata {

	definition (name: "Hildebrand Glow", namespace: "BirdsLikeWires", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/main/hildebrand/drivers/glow_mqtt.groovy") {

		capability "SignalStrength"

		command "disconnect"

		attribute "healthStatus", "enum", ["offline", "online"]

		if (debugMode) {
			command "testCommand"
		}

	}

}


preferences {

	input name: "cloudActive", type: "bool", title: "Use Hildebrand Cloud MQTT Broker", defaultValue: true
	input name: "cloudId", type: "text", title: "Device ID:", required: true
	input name: "mqttBroker", type: "text", title: "Local MQTT Broker Address:", required: false
	input name: "mqttUser", type: "text", title: "MQTT Username:", required: false
	input name: "mqttPass", type: "password", title: "MQTT Password:", required: false

	input name: "infoLogging", type: "bool", title: "Enable logging", defaultValue: true
	input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
	input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false

}


void testCommand() {

	logging("${device} : Test Command", "info")

}


void configureSpecifics() {
	// Called by main configure() method in BirdsLikeWires.library

	device.name = "Hildebrand Glow"

	state.occasionalUpdated = 0

	removeDataValue("firmware")
	removeDataValue("mac")
	removeDataValue("model")
	removeDataValue("smetsversion")
	removeDataValue("zigbee")

	// Create child devices.
	fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Electricity")
	fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Gas")

}


void updateSpecifics() {
	// Called by updated() method in BirdsLikeWires.library

	disconnect()

	if (settings?.cloudActive) {
		state.mqttBroker = "glowmqtt.energyhive.com"
		state.mqttTopic = settings?.cloudId ? "SMART/HILD/${cloudId}" : ""
		state.parsePasses = 1			// Cloud MQTT is received as 1 message and would need parsing in one pass.
	} else {
		state.mqttBroker = settings?.mqttBroker
		state.mqttTopic = "glow/#"
		state.parsePasses = 3			// Local MQTT is received as 3 seperate messages (STATE plus two SENSORS), parsed sequentially.
	}

	state.parseCounter = 0				// We update some variables only every occasionalUpdateMinutes, so we must count our parse() passes.

	schedule("0/10 * * * * ? *", mqttConnect)

}


void disconnect() {
	// Disconnect from broker.

	interfaces.mqtt.disconnect()
	logging("${device} : MQTT : Disconnected from broker ${state.mqttBroker} (${state.mqttTopic}).", "info")

}


void uninstalled() {
	// Runs when device is removed.

	interfaces.mqtt.disconnect()
	logging("${device} : Uninstalled", "info")

}


void parse(String description) {

	updateHealthStatus()
	checkDriver()

	// The parse is expected to run state.parsePasses number of times.
	Integer parseMax = state.parsePasses
	Integer parseCount = state.parseCounter + 1

	logging("${device} : Parse (Pass $parseCount/$parseMax) : $description", "trace")

	// Some values should only be updated every occasionalUpdateMinutes to reduce chatter and hub load.
	Boolean updateOccasional = false
	Integer updateCount = 0
	long occasionalUpdateMillis = occasionalUpdateMinutes * 60000
	long occasionalElapsedMillis = state.updatedHealthStatus - state.occasionalUpdated

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

		def eleMeter = fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Electricity")
		def gasMeter = fetchChild("BirdsLikeWires","Hildebrand Glow Meter","Gas")

		if (settings?.cloudActive) {

			logging("${device} : Format : Cloud", "debug")

			// State
			int lqi = Integer.parseInt(json.pan.lqi, 16)
			sendEvent(name: "lqi", value: lqi)

			int rssi = Integer.parseInt(json.pan.rssi, 16)
			String twosComplement = Integer.toHexString((-1 * rssi));
			rssi = zigbee.convertHexToInt(twosComplement[-2..-1]) * -1
			sendEvent(name: "rssi", value: rssi)

			// Electricity
			BigDecimal cumulative = Integer.parseInt(json.elecMtr."0702"."00"."00", 16) / 1000
			cumulative = cumulative.setScale(3, BigDecimal.ROUND_HALF_UP)
			eleMeter.parse([[name:"energy", value:cumulative]])

			int power = Integer.parseInt(json.elecMtr."0702"."04"."00", 16)
			eleMeter.parse([[name:"power", value:power]])

			//logging("${device} : cumulative : $cumulative", "warn")

			if (updateOccasional) {

				// State
				updateDataValue("model", "${json.hversion}")
				updateDataValue("smetsversion", "${json.smetsVer}")
				updateDataValue("zigbee", "${json.zbSoftVer}")


				// Electricity
				String eleSupplier = json.elecMtr."0708"."01"."01"
				eleMeter.setState([[name:"supplier", value:eleSupplier]])


				// Gas
				String gasSupplier = json.gasMtr."0708"."01"."01"
				gasMeter.setState([[name:"supplier", value:gasSupplier]])

			}

		} else {

			logging("${device} : Format : Local", "debug")

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

				BigDecimal cumulativeExport = json.electricitymeter.energy.export.cumulative
				String cumulativeExportUnits = json.electricitymeter.energy.export.units
				eleMeter.parse([[name:"export", value:cumulativeExport, unit: "${cumulativeExportUnits}"]])

				BigDecimal cumulative = json.electricitymeter.energy.import.cumulative
				String cumulativeUnits = json.electricitymeter.energy.import.units
				eleMeter.parse([[name:"energy", value:cumulative, unit: "${cumulativeUnits}"]])

				BigDecimal power = json.electricitymeter.power.value * 1000
				power = power.intValue()
				eleMeter.parse([[name:"power", value:power, unit: "W"]])

				if (updateOccasional) {

					eleMeter.setState([[name:"energyUnit", value:"${cumulativeUnits}"]])
					eleMeter.setState([[name:"exportUnit", value:"${cumulativeExportUnits}"]])
					eleMeter.setState([[name:"powerUnit", value:"W"]])

					BigDecimal day = json.electricitymeter.energy.import.day
					BigDecimal week = json.electricitymeter.energy.import.week
					BigDecimal month = json.electricitymeter.energy.import.month

					eleMeter.parse([[name:"day", value:day]])
					eleMeter.parse([[name:"week", value:week]])
					eleMeter.parse([[name:"month", value:month]])

					String mpan = json.electricitymeter.energy.import.mpan
					eleMeter.setState([[name:"mpan", value:mpan]])

					BigDecimal unitrate = json.electricitymeter.energy.import.price.unitrate
					BigDecimal unitratePence = unitrate * 100
					unitratePence = unitratePence.setScale(2, BigDecimal.ROUND_HALF_UP)
					eleMeter.parse([[name:"unitrate", value:unitratePence]])
					eleMeter.setState([[name:"unitrateUnits", value:"p/kWh"]])

					BigDecimal standingcharge = json.electricitymeter.energy.import.price.standingcharge
					BigDecimal standingchargePence = standingcharge * 100
					standingchargePence = standingchargePence.setScale(2, BigDecimal.ROUND_HALF_UP)
					eleMeter.parse([[name:"standingcharge", value:standingchargePence]])
					eleMeter.setState([[name:"standingchargeUnits", value:"p/day"]])

					String supplier = json.electricitymeter.energy.import.supplier
					eleMeter.setState([[name:"supplier", value:supplier]])

				}

			} else if (msg.topic.indexOf('gasmeter') >= 0) {


				if (updateOccasional) {
					// Gas meters only send data every 30 minutes via the electricity meter, so there's no point being verbose.

					BigDecimal volume = json.gasmeter.energy.import.cumulativevol
					gasMeter.parse([[name:"volume", value:volume]])
					String volUnits = json.gasmeter.energy.import.cumulativevolunits
					gasMeter.setState([[name:"volumeUnits", value:"${volUnits}"]])

					BigDecimal cumulative = json.gasmeter.energy.import.cumulative
					gasMeter.parse([[name:"energy", value:cumulative]])
					String units = json.gasmeter.energy.import.units
					gasMeter.setState([[name:"energyUnit", value:"${units}"]])

					BigDecimal day = json.gasmeter.energy.import.day
					BigDecimal week = json.gasmeter.energy.import.week
					BigDecimal month = json.gasmeter.energy.import.month

					gasMeter.parse([[name:"day", value:day]])
					gasMeter.parse([[name:"week", value:week]])
					gasMeter.parse([[name:"month", value:month]])

					String mprn = json.gasmeter.energy.import.mprn
					gasMeter.setState([[name:"mprn", value:mprn]])

					BigDecimal unitrate = json.gasmeter.energy.import.price.unitrate
					BigDecimal unitratePence = unitrate * 100
					unitratePence = unitratePence.setScale(2, BigDecimal.ROUND_HALF_UP)
					gasMeter.parse([[name:"unitrate", value:unitratePence]])
					gasMeter.setState([[name:"unitrateUnits", value:"p/kWh"]])

					BigDecimal standingcharge = json.gasmeter.energy.import.price.standingcharge
					BigDecimal standingchargePence = standingcharge * 100
					standingchargePence = standingchargePence.setScale(2, BigDecimal.ROUND_HALF_UP)
					gasMeter.parse([[name:"standingcharge", value:standingchargePence]])
					gasMeter.setState([[name:"standingchargeUnits", value:"p/day"]])

					String supplier = json.gasmeter.energy.import.supplier
					gasMeter.setState([[name:"supplier", value:supplier]])

				}

			}

		}
	  
	} catch (Exception e) {

		logging("${device} : Parse : ${e.message}", "error")

	}

	if (parseCount == parseMax) {

		state.parseCounter = 0

		if (updateOccasional) {

			state.occasionalUpdated = state.updatedHealthStatus

		}

	} else {

		state.parseCounter = parseCount

	}

}
