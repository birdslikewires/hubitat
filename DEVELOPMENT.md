# Zigbee Development Stuff


## Standard Library Command Equivalents

I find that when adding support for a specific device the library commands are not always that useful, otherwise I probably wouldn't be writing a custom driver. 

### zigbee.setLevel()

	he cmd 0x${device.deviceNetworkId} 0x01 0x0008 0x04 {${hexLevel} ${hexDuration} 00}

###	zigbee.off()
	
	he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}

###	zigbee.on()

	he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}

### zigbee.onOffRefresh()

	he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0x0000 {}
	he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0x0000 {}


## Reporting

This normally forms the basis of the `healthStatus` value as we really want devices to report in on their own, rather than polling. And we need the data to keep our statuses up to date, of course!

	zigbee.configureReporting(cluster, attribute, encoding, minReportTime, maxReportTime, reportableChange)

This tends to work reliably. You can grab the `cluster`, `attribute` and `encoding` from the debug messages received for parsing. Use `minReportTime` to prevent that cluster reporting more often than that value and `maxReportTime` for how often you want to receive a message. Values are in seconds. It appears that '1' works for `reportableChange` most of the time.

Here are a few examples for reporting every 10 minutes. The `minReportTime` affects the reponse time when (for example) a relay is actuated, so you want '1' or '0' here. I only use '1' as a safety measure in case something goes weird and the device sends too many messages, though I've never actually seen that happen.

	int minReportTime = 1
	int maxReportTime = 600
	int reportableChange = 1

	zigbee.configureReporting(0x0006, 0x0000, 0x0010, minReportTime, maxReportTime, reportableChange)			// switch relay state (one relay)
	zigbee.configureReporting(0x0006, 0x00FF, 0x0010, minReportTime, maxReportTime, reportableChange)			// switch relay state (multiple relays)
    zigbee.configureReporting(0x0702, 0x0000, DataType.UINT48, minReportTime, maxReportTime, reportableChange)	// energy reporting

If a device has no useful value to report (perhaps it's just a repeater) then asking for its model name usually works.

	zigbee.configureReporting(0x0000, 0x0005, 0x0042, minReportTime, maxReportTime, reportableChange)			// model information

Very occasionally you'll get a device that wants this attribute thing [:] that I don't understand, plus the delay value for the message.

	zigbee.configureReporting(0x0000, 0x0005, 0x0042, minReportTime, maxReportTime, reportableChange, [:], 200)	// model information


## HE Raw Zigbee Frame

`List cmds = ["he raw 0x${device.deviceNetworkId} 1 1 0x0501 {09 01 00 04}"]`

```
he raw 
0x${device.deviceNetworkId} 16 bit hex address 
1							source endpoint, always one				 
1 							destination endpoint, device dependent
0x0501 						zigbee cluster id
{09 						frame control
	01 						sequence, always 01
		00 					command
			04}				command parameter(s)
```


## HE Raw Zigbee Frame for AlertMe

`he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00EE {11 00 02 01 01} {0xC216}`


## Zigbee Commands Reference

````
def onOffRefresh(){
	readAttribute(ONOFF_CLUSTER, 0x0000)
}

def levelRefresh(){
	readAttribute(LEVEL_CONTROL_CLUSTER, 0x0000)
}

def hueSaturationRefresh(){
	readAttribute(COLOR_CONTROL_CLUSTER, 0x0000) + readAttribute(COLOR_CONTROL_CLUSTER, 0x0001)
}

def colorTemperatureRefresh() {
	readAttribute(COLOR_CONTROL_CLUSTER, 0x0007)
}

def electricMeasurementPowerRefresh() {
	readAttribute(ELECTRICAL_MEASUREMENT_CLUSTER, 0x050B)
}

def simpleMeteringPowerRefresh() {
	readAttribute(SIMPLE_METERING_CLUSTER, 0x0400)
}

def onOffConfig(minReportTime=0, maxReportTime=600) {
	configureReporting(ONOFF_CLUSTER, 0x0000, DataType.BOOLEAN, minReportTime, maxReportTime, null)
}

def levelConfig(minReportTime=1, maxReportTime=3600, reportableChange=0x01) {
	configureReporting(LEVEL_CONTROL_CLUSTER, 0x0000, DataType.UINT8, minReportTime, maxReportTime, reportableChange)
}

def simpleMeteringPowerConfig(minReportTime=1, maxReportTime=600, reportableChange=0x05) {
	configureReporting(SIMPLE_METERING_CLUSTER, 0x0400, DataType.INT24, minReportTime, maxReportTime, reportableChange)
}

def electricMeasurementPowerConfig(minReportTime=1, maxReportTime=600, reportableChange=0x0005) {
	configureReporting(ELECTRICAL_MEASUREMENT_CLUSTER, 0x050B, DataType.INT16, minReportTime, maxReportTime, reportableChange)
}

def colorTemperatureConfig(minReportTime=1, maxReportTime=3600, reportableChange=0x10) {
	configureReporting(COLOR_CONTROL_CLUSTER, 0x0007, DataType.UINT16, minReportTime, maxReportTime, reportableChange)
}

def batteryConfig(minReportTime=30, maxReportTime=21600, reportableChange=0x01) {
	configureReporting(POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, minReportTime, maxReportTime, reportableChange)
}

def temperatureConfig(minReportTime=30, maxReportTime=3600, reportableChange=0x0064) {
	configureReporting(TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000, DataType.INT16, minReportTime, maxReportTime, reportableChange)
}

def iasZoneConfig(minReportTime=0, maxReportTime=SECONDS_IN_HOUR) {
	enrollResponse() +
		configureReporting(IAS_ZONE_CLUSTER, ATTRIBUTE_IAS_ZONE_STATUS, DataType.BITMAP16, minReportTime, maxReportTime, null) +
		configureReporting(IAS_ZONE_CLUSTER, ATTRIBUTE_IAS_ZONE_CIE_ADDRESS, DataType.IEEE_ADDRESS, minReportTime, maxReportTime, null)
}
````
[https://community.smartthings.com/t/zigbee-something-commands-reference/110615/5](https://community.smartthings.com/t/zigbee-something-commands-reference/110615/5)
