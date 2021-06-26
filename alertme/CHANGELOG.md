# AlertMe Changelog

## 26th June 2021

- Automatic re-enrolment of devices after power loss now appears to work.
- Responses are sent to IAS enrol and match descriptor (cluster 0006) requests from devices. 

## 24th January 2021

- Report as 'not present' once batteryOkay is false on battery-only devices.

## 18th January 2021

- Added hub uptime check to checkPresence() which prevents notification panic upon reboots.
- Presence checking no longer overwrites other states when presence is lost.

## 10th January 2021

- Added debouncing to the Key Fob. Please hit "Configure" on your key fob devices to set things up properly, as the driver includes changes to the state variables.