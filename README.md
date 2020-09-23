# Hubitat Drivers

## Instructions

Instructions mostly live [here](https://community.hubitat.com/t/release-alertme-device-drivers) for AlertMe devices.

Details on the Salus SP600 driver are [here](https://community.hubitat.com/t/release-salus-sp600-smart-plug-with-presence).

## Development Stuff

#### HE Raw Zigbee Frame

List cmds = ["he raw 0x${device.deviceNetworkId} 1 1 0x0501 {09 01 00 04}"]

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