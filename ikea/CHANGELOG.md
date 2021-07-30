# IKEA Changelog

## 29th July 2021

- Had a whirl at making my own Shortcut Button (E1812) driver.
- Press, hold and release are all recognised and debounced.
- Battery reporting configured for a message every hour.
- Presence detection relies upon the battery report; as these are sleepy endpoints with small batteries I'm trialling hourly reports and we'll see how long they last. As such presence sort-of-works, but needs some more tweaking.
- Plenty of tidying to be done, as this was based upon my AlertMe Button driver which has a bit more to cope with.