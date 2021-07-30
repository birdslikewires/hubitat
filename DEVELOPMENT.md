# Zigbee Development Stuff

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