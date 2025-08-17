# Hildebrand Glow Drivers for Hubitat

In the UK many homes are being fitted with smart meters for domestic electricity and gas.

Meter upgrades are performed on behalf of the supplier and report your usage and meter readings via the [Data Communications Company](https://www.smartdcc.co.uk/) (DCC). When a smart meter is fitted the homeowner will normally be provided with an In-Home Display (IHD) which is pretty much the extent of most consumer's involvement with the meter.

However, there is one company, [Hildebrand](http://hildebrand.co.uk/), registered as a "DCC Other User" who are permitted to receive communication directly from smart meters and historical data from the DCC. They can provide an IHD which also operates as a Consumer Access Device (CAD) and can provide a **local** MQTT feed to an MQTT broker.

Search for "**hildebrand**" on Hubitat Package Manager or you can install manually:

- [BirdsLikeWires Library Bundle](https://github.com/birdslikewires/hubitat/blob/main/generic/libraries/BirdsLikeWires.library/BirdsLikeWires.library.groovy) - Bundle URL: [ZIP](https://github.com/birdslikewires/hubitat/raw/main/generic/libraries/library.zip)
- [Hildebrand Glow MQTT Driver](https://github.com/birdslikewires/hubitat/blob/main/hildebrand/drivers/glow_mqtt.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/main/hildebrand/drivers/glow_mqtt.groovy)
- [Hildebrand Glow MQTT Driver](https://github.com/birdslikewires/hubitat/blob/main/hildebrand/drivers/glow_meter_child.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/main/hildebrand/drivers/glow_meter_child.groovy)

All three components are required and the Library can be installed on Developer Tools > Bundles > Import ZIP, though HPM is the easier way to go.

These drivers subscribe to the appropriate topic on your local MQTT broker and fettle the data into two Hubitat child devices, one for your electricity and one for your gas meter. This provides:

- Accurate power and energy readings every 10 seconds for electricity.
- Meter readings in kWh (coming soon, m3 for gas).
- Daily, weekly and monthly cumulative energy readings.
- Billing information (rates and standing charges).
- Meter Point Administration Number (MPAN) for electricity meters.
- Meter Point Reference Number (MPRN) for gas meters.

Being direct from the meter these readings are highly accurate and far better than the usual "clamp" methods used on the incoming cables.
