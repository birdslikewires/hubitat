# AlertMe Drivers for Hubitat

These drivers provide support for all of the common AlertMe devices in the UK, and probably many of those available as *"Iris V1"* devices in the US which were built on the same platform. After examining some [code](http://www.desert-home.com/search/label/Iris) [from](https://jeelabs.net/boards/6/topics/285?page=2) [the](https://forum.alertme.org.uk/viewtopic.php?f=4&t=97&start=20) [past](https://github.com/jamesleesaunders/PyAlertMe) and with @markus's invaluable help deciphering some data blocks, here are my first Hubitat drivers!

All of these drivers feature presence detection for troubleshooting and system control (using key fobs) plus a 'ranging mode' for checking link quality (LQI). While in ranging mode device LEDs will double-flash to show a good quality link, or triple flash if the LQI is poor. This measurement is transmitted back to the Hub and shown on the device page. It's also handy if you have a pile of devices in front of you and you've forgotten which is which.

## Driver Import

### Hubitat Package Manager

These drivers are available through HPM, just search for "AlertMe".

### Driver Import

Head to *Drivers Code* from the *Advanced* section of the side bar, click *New Driver* and *Import.* Paste in the RAW URL from below and hit *Save*.

- [AlertMe Alarm Sensor](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_alarm.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_alarm.groovy)

The Alarm Sensor also mimics a Motion Sensor as the sound detection capability is not supported in all areas of Hubitat just yet. The driver reports both sound and 'motion'.

- [AlertMe Button](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_button.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_button.groovy)

- [AlertMe Contact Sensor](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_contact.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_contact.groovy)

- [AlertMe Key Fob](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_keyfob.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_keyfob.groovy)

- [AlertMe Lamp](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_lamp.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_lamp.groovy)

The Lamp is a desktop indicator rather than a room-illuminating 'bulb', with the ability to run sequences based on RGB colours, fade duration and dwell times. The Lamp has an internal battery and should (!) act as a repeater.

- [AlertMe Motion Sensor](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_motion.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_motion.groovy)

- [AlertMe Power Clamp](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_powerclamp.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_powerclamp.groovy)

Power usage in Watts, power summary in Kilowatt-hours, battery voltage and percentage, temperature and device uptime.

- [AlertMe Smart Plug](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_smartplug.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_smartplug.groovy)

Relay on and off, power usage in Watts, power summary in Kilowatt-hours, battery presence, voltage, percentage and charging state, supply presence, state mismatch (for warning when there is load demand but the supply has failed) and temperature (skewed and roughly corrected). These outlets also act as repeaters on mains and battery power, so their feature set is pretty much the best I've ever found.

## Device Maintenance

If your smart plugs power down when unplugged from the supply, their internal rechargeable batteries have expired. Here's how to replace them, after which they'll run for ages with mains power off.

[![Pointing at a battery inside a dismantled AlertMe smart plug.](https://img.youtube.com/vi/t5y5-Hrukxc/0.jpg)](https://www.youtube.com/watch?v=t5y5-Hrukxc)

## The Future

There are no doubt errors and omissions, pull requests are gratefully received!

If you do end up using these, please post here to let me know, always good to hear that someone's finding such things useful.
