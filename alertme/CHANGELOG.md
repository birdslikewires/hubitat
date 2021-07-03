# AlertMe Changelog

## 3rd July 2021

- Tweaks to logging timeouts and presence reporting.
- On reboot we _should_ default to "present" with no state change, allowing a state change back to "not present" (triggering a notification if configured) when the device doesn't report in.

## 27th June 2021

- Forgot to mention that "info", "debug" and "trace" logging now auto-disable after 30, 20, and 10 minutes respectively. This is due to the "chattiness" of the AlertMe devices causing strain on Hubitat's logging system.
- Logging oopsie corrected on the Button driver.
- Battery warnings now start below 20% remaining.

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