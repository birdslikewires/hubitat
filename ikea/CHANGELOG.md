# IKEA Trådfri Drivers for Hubitat - Changelog

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