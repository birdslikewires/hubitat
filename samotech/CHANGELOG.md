# Samotech Drivers for Hubitat - Changelog

## 7th February 2022

- Updated library and a few minor quality-of-life tweaks.

## 10th January 2022

- Deal with (or rather, don't deal with) duplicate actuation response messages. https://github.com/birdslikewires/hubitat/issues/12
- Device reporting (on cluster 0x0702) is only available on SM308-2CH, so ditched that idea. Pinging from the hub works fine.
- Device name is now correctly configured.

## 8th January 2022

- Updated the Library section.

## 7th January 2022

- Does support the SM308-2CH. Hooray!
- Added parent device debouncing for when there are too many children shouting.

## 6th January 2022

- Initial commit of the SM308 Series driver. Supports both the SM308 and SM308-S.
- Doesn't support the SM308-2CH. Yet. Hopefully.
