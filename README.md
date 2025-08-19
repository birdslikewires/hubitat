# Hubitat Drivers

Welcome to my repository of Hubitat stuff.

The [AlertMe](https://github.com/birdslikewires/hubitat/tree/main/alertme) driver section is certainly the _Magnum Opus_ of this repo. All of the Zigbee, none of the standards! This stuff was on sale before the book was written. Grab yourself a [Hubitat C7](https://hubitat.com/products?region=280262967339) or earlier and your AlertMe / Iris V1 gear is back in action.

At the other end of the alphabet is the [Zigbee2MQTT Routing Driver](https://github.com/birdslikewires/hubitat/tree/main/zigbee2mqtt). After a long time hacking away at raw, undocumented, manufacturer-specific Zigbee commands, I discovered that some things just aren't possible on Hubitat. Not everything gets passed through to the driver by the platform, and devices requiring pairing codes and multiple layers of encryption aren't supported. But with [Zigbee2MQTT](https://www.zigbee2mqtt.io/) there's a whole world of developers out in the world to lean on, so this "Routing Driver" creates Hubitat devices from its MQTT output.

For everything else, just click on the driver directory and there may be some documentation. Otherwise it'll live on the post for the driver on [Hubitat Community forum](https://community.hubitat.com). You can check [BirdsLikeWires topics in the Custom Drivers section](https://community.hubitat.com/search?q=%5BRELEASE%5D%20%23comappsanddrivers%3Acommunity-drivers%20%40birdslikewires%20in%3Atitle) for the latest.