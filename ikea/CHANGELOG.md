# IKEA Symfonisk and Trådfri Drivers for Hubitat - Changelog

## 7th February 2022

- Reorganised and updated a bunch o' code for E1744.

## 6th February 2022

- Reorganised and updated a bunch o' code for E1812.
- Support added for v2.3.080 firmware and doubleTap function.

## 6th January 2022

- The å character was making it tricky for people to find the driver, so I have relented.
- The removeDataValue() function was introduced with HE v2.2.1, so that's our new minimum.

## 20th December 2021

- Calculate the device presence timeout threshold automatically.
- Tidied init process.

## 24th November 2021

- Corrected silly error with the battery voltage report, though I don't know if it will ultimately help with the readings.
- Reporting intervals and presence detection intervals are now specified in minutes.

## 24th October 2021

- Uncoupled battery state from presence detection.
- Skip device announcement broadcast messages.

## 18th October 2021

- Increase reporting interval from every hour to every two hours.

## 10th August 2021

- Mimic the physical button push when triggered on/off in software, otherwise automations won't run.

## 5th August 2021

- Added switch on/off for compatibility with Mirror app (E1744).
- Remote can now be used as a lighting dimmer controller (E1744).

## 4th August 2021

- Added the Symfonisk Remote (E1744) driver.
- Presents as a three button device for press, clockwise and anticlockwise rotation.
- Pushed, held and released are recognised for button presses and rotations.
- Official method for double tap supported, triple tap shown as a release event.
- Battery data from the device seems to be nonsense, so we're fudging it.

## 30th July 2021

- General cleanup and nonsense removal.
- Simulated push / hold / release events are now supported.
- Remarkably, everything held up overnight and appears to work!

## 29th July 2021

- Had a whirl at making my own Shortcut Button (E1812) driver.
- Press, hold and release are all recognised and debounced.
- Battery reporting configured for a message every hour.
- Presence detection relies upon the battery report; as these are sleepy endpoints with small batteries I'm trialling hourly reports and we'll see how long they last. As such presence sort-of-works, but needs some more tweaking.
- Plenty of tidying to be done, as this was based upon my AlertMe Button driver which has a bit more to cope with.