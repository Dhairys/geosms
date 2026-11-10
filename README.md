# Locate and Text

Android app that shares a GPS location by SMS, on request or on a timer, with no internet needed. The asking phone shows the answer on a map.

## Build and run

1. Install Android Studio and the Android 14 (API 34) SDK from the SDK Manager.
2. File > Open, choose this folder (the one containing `settings.gradle.kts`) and wait for the Gradle sync to finish.
3. Run configuration must be of type **Android App** with module `app`. A plain Kotlin or Application configuration will not install anything.
4. Plug in a phone with USB debugging on and press Run. Install on both phones, or copy the APK from Build > Build APK(s).

## Two roles, one app

Pick a role at the top of the screen. Each role has its own daily text limit.

| Role | What it does | Default daily limit |
| --- | --- | --- |
| Share my location | Replies with GPS when asked, or sends on a timer | 3000 (for a 3600 a day plan) |
| Ask for location | Requests the other phone's location | 50 (for a 100 a day plan) |

Set the limit a little under your plan so your own texts still go through. The count restarts at midnight.

## Using it

1. On both phones, enter the other phone's number with country code, the same way on each.
2. On the phone to be located: choose Share my location, keep "Reply only when asked" on, tap Start sharing and allow every permission.
3. On the other phone: choose Ask for location, then tap "Ask by text" (1 text) or "Ask by missed call" (no text).
4. The located phone gets its GPS fix (up to a minute outdoors) and replies with 1 text:
   `LOC 40.748817,-73.985428 acc=12m t=1731150000 https://maps.google.com/?q=40.748817,-73.985428`
   If it cannot get a fix it replies `NOFIX`.
5. The asking phone shows the distance, direction, coordinates and a map pin, and can open the spot in any maps app.

One check costs 1 text on each phone. You can also text `WHERE` by hand.

## Timed mode

Switch off "Reply only when asked" on the sharing phone to send automatically every N seconds (10 to 3600). After each interval it sends only if you moved 50 m or more since the last text, plus a check-in at least once an hour. The daily limit still applies.

## Missed call

On the sharing phone, keep "Answer a missed call" on and allow the Phone and Call log permissions. A call from the saved number that rings under 12 seconds and is never answered counts as a request. Other numbers, answered calls and longer rings are ignored. Needs Android 9 or newer.

## Offline behavior

- GPS needs no internet. Texts and calls need cell signal only.
- Received locations always show coordinates plus distance and compass direction, with no map.
- Map pictures need internet on the asking phone, and areas you viewed before are cached.

## Notes

- Only texts and calls from the saved number are acted on. Numbers can be faked by a determined person, so this is convenience, not security.
- The sharing phone shows a "Location shared" notification every time it answers.
- Google Play restricts apps that send SMS in the background, so install the APK directly.
- After a reboot or force stop, open the app and tap Start sharing again.
- Keep battery optimisation off for the app on phones that stop background services.
- iPhone does not allow apps to send texts automatically, so this works on Android only.
