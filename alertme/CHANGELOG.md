# AlertMe Changelog

## 18th January 2021

- Added hub uptime check to checkPresence() which prevents notification panic upon reboots.
- Presence checking no longer overwrites other states when presence is lost.

## 10th January 2021

- Added debouncing to the Key Fob. Please hit "Configure" on your key fob devices to set things up properly, as the driver includes changes to the state variables.