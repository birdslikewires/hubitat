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


## HE Raw Zigbee Frames

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

## HE Raw Zigbee Frame for AlertMe

he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00EE {11 00 02 01 01} {0xC216}
