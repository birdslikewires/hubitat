# Xiaomi Aqara Drivers for Hubitat - Changelog

## 20th February 2022

- Read map.value into a string at the beginning of the processMap method.

## 19th February 2022

- Bind and configuration messages are now sent as part of the configure() method (GZCGQ01LM).
- Looks like our requested values are either ignored or out of range, the interval always stays around 8 seconds (GZCGQ01LM).
- Do not turn off debug and trace logs when hitting Configure, as they may be in use. Allow the scheduled tasks to do this (GZCGQ01LM).
- Configuration temporary state can be either "set" or "receieved". The former confirms local settings, the latter the device (GZCGQ01LM).
- Some minor tidying and tweaking (GZCGQ01LM).

## 11th January 2022

- Added press debouncing (WXKG06LM / WXKG07LM).
- Refactored the code a bit.

## 10th January 2022

- Device name now set correctly when configured (WXKG06LM / WXKG07LM).

## 9th January 2022

- Added WXKG06LM fingerprint to the WXKG07LM driver and tweaked name.
- Added "autorelease" feature to make use of a weird message received after a button hold.

## 31st December 2021

- Initial driver for the GZCGQ01LM light sensor.
- Added reporting of pressure direction (rising and falling) to WSDCGQ11LM.

## 28th December 2021

- Initial driver for the WSDCGQ11LM temperature and humidity sensor.

## 22nd December 2021

- Initial driver for the WXKG12LM wireless mini switch.

## 21st December 2021

- Initial driver for the WXKG07LM wireless remote switch.

## 20th December 2021

- Initial driver for the WXKG11LM wireless mini switch.