# AlertMe Drivers for Hubitat

These drivers provide support for all of the common AlertMe devices in the UK, and probably many of those available as *"Iris V1"* devices in the US which were built on the same platform. After examining some [code](http://www.desert-home.com/search/label/Iris) [from](https://jeelabs.net/boards/6/topics/285?page=2) [the](https://forum.alertme.org.uk/viewtopic.php?f=4&t=97&start=20) [past](https://github.com/jamesleesaunders/PyAlertMe) and with @markus's invaluable help deciphering some data blocks, here are my first Hubitat drivers!

All of these drivers feature presence detection for troubleshooting and system control (using key fobs) plus a 'ranging mode' for checking link quality (LQI). While in ranging mode device LEDs will double-flash to show a good quality link, or triple flash if the LQI is poor. This measurement is transmitted back to the Hub and shown on the device page. It's also handy if you have a pile of devices in front of you and you've forgotten which is which.

## Installation

Search for "**alertme**" on Hubitat Package Manager **(HPM v1.8.7 or later is required)** or you can install manually by copy-and-pasting the RAW code into *Developer Tools > Drivers Code > New Driver*.

- [AlertMe Alarm Sensor](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_alarm.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_alarm.groovy)

The **Alarm Sensor** responds when it hears other alarm sirens. It also mimics a Motion Sensor in its response, as the sound detection capability is not supported in all areas of Hubitat just yet, the driver reporting both sound and 'motion'. Presence, tamper detection, temperature and battery voltage are supported.

- [AlertMe Button](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_button.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_button.groovy)

The **Button** has both *"Push"* and *"Hold"* triggers. Presence, tamper detection, temperature and battery voltage are supported.

- [AlertMe Contact](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_contact.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_contact.groovy)

The **Contact** sensor supports the expected *"Open"* and *"Closed"* triggers. Presence, tamper detection, temperature and battery voltage are supported.

- [AlertMe Fob](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_fob.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_fob.groovy)

The **Fob** supports both *"Home"* and *"Away"* button triggers. Presence and battery voltage are also supported. *Please get in touch if you know how to trigger the confirmation sounds on this device!*

- [AlertMe Lamp](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_lamp.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_lamp.groovy)

The **Lamp** is a desktop indicator rather than a room-illuminating 'bulb', with the ability to run sequences based on RGB colours, fade duration and dwell times. The Lamp has an internal battery and should (!) act as a repeater. Presence and battery voltage are supported.

- [AlertMe Motion](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_motion.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_motion.groovy)

The **Motion** sensor supports motion detection, as you may have expected. Presence, tamper detection, temperature and battery voltage are also supported.

- [AlertMe Pendant](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_pendant.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_pendant.groovy)

The **Pendant** is essentially a Fob running specialised firmware. It can be used as a standard one-button Fob by reassigning the Fob driver, or it can be used in the manner of a critical care pendant.

Pressing either button registers a *Push* event and sets the device into "Help Needed" mode, signified by a single beep and single red flash. The Pendant stays in this mode, repeating the call every 30 seconds until the hub acknowledges the request, at which point it switches to "Help Called" mode, beeping twice and flashing the LEDs red continuously. This mode cannot be exited from the Pendant.

The *Push* events from the "Help Needed" mode should be used to trigger some form of assistance. Once that assistance is confirmed the Pendant can be switched "on", which triggers "Help Coming" mode. This is indicated to the user by three beeps and the LEDs flashing green continuously.

The Pendant can be set back to "Idle" mode by being switched "off".

**NOTE:** Though you could use this driver to create a facsimile of a critical care pendant system, absolutely no guarantee of reliability or suitability is given or implied. 

- [AlertMe Power Clamp](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_powerclamp.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_powerclamp.groovy)

The *Power Clamp* is a device for measuring power usage on an AC circuit, using a small clamp which attaches around an incoming domestic live cable. Power usage in Watts, power summary in Kilowatt-hours, tamper, battery voltage and percentage, temperature and device uptime are all supported.

- [AlertMe Smart Plug](https://github.com/birdslikewires/hubitat/blob/master/alertme/drivers/alertme_smartplug.groovy) - Import URL: [RAW](https://raw.githubusercontent.com/birdslikewires/hubitat/master/alertme/drivers/alertme_smartplug.groovy)

The *Smart Plug* is a sophisticated remote-control power monitoring passthrough. We support relay on and off, power usage in Watts, power summary in Kilowatt-hours, battery presence, voltage, percentage and charging state, supply presence and state mismatch (for warning when there is load demand but the supply has failed). These outlets also act as repeaters on mains and battery power, so their feature set is pretty much the best I've ever found.

## Device Maintenance

If your smart plugs power down when unplugged from the supply, their internal rechargeable batteries have expired. Here's how to replace them, after which they'll run for ages with mains power off.

[![Pointing at a battery inside a dismantled AlertMe smart plug.](https://img.youtube.com/vi/t5y5-Hrukxc/0.jpg)](https://www.youtube.com/watch?v=t5y5-Hrukxc)

## The Future

There are no doubt errors and omissions, pull requests are gratefully received!

If you do end up using these, please post here to let me know, always good to hear that someone's finding such things useful.
