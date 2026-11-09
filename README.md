# Locate and Text

Android app that texts your GPS position to one phone number on a timer, with no internet needed, and shows locations it receives.

## Build

1. Install Android Studio.
2. File > Open, choose this folder, let Gradle sync (Studio creates the Gradle wrapper).
3. Plug in a phone with a SIM (USB debugging on) and press Run, or Build > Build APK(s) and copy the APK over.
4. Install it on both phones if both should see each other.

## Use

1. Enter the other person's number and how often to send (minutes).
2. Tap Start sharing and allow Location, SMS and Notifications.
3. The other phone gets a text like:
   `LOC 40.748817,-73.985428 acc=12m t=1731150000 https://maps.google.com/?q=40.748817,-73.985428`
   With the app installed it plots it automatically. Without the app, the link opens in any maps app once they have data.

## Asking for a location (the cheapest way to use 100 texts)

Leave "On demand only" ticked on the phone being located and tap Start sharing. GPS stays off, so the battery lasts, and nothing is sent on its own.

- On the other phone, tap "Ask for their location now". That sends the text `WHERE`.
- The located phone wakes its GPS, gets a fix (up to a minute outdoors), and texts the position back. If it cannot get any fix it texts `NOFIX`.
- You can also text `WHERE` by hand from any phone app. Each check costs exactly 1 text on each phone: 1 for the request, 1 for the reply (a fix, or `NOFIX`, never both). The reply is a single SMS under 160 characters, and a second `WHERE` that arrives while one is being answered is ignored. With the default limit of 50 per day, each phone can do 50 checks a day.
- The located phone always shows a "Location shared" notification when it answers.
- Sharing has to be switched on (Start sharing) on the located phone, and only the number entered in the app is answered. Phone numbers on texts can be faked by a determined person, so treat this as convenience, not security.
- After a reboot or force stop, open the app and tap Start sharing again. Android will not restart it by itself.
- Untick "On demand only" for timed sending instead.

## Asking by missed call (no text used by the asker)

- On the located phone, keep "Also answer a missed call from this number" ticked, tap Start sharing, and allow the Phone and Call log permissions. Call log access is how Android tells the app who is calling.
- On the other phone, tap "Ask by missed call". It rings the number for about 7 seconds and hangs up. No text is used on that phone, and unanswered calls are usually free, but check your plan.
- The located phone sees a short ring from the saved number that nobody answers, treats it as a request, and sends 1 text with the location, exactly like a `WHERE` text. That reply counts toward its daily limit.
- Only a call from the number saved in the app counts. Calls from any other number are ignored. A normal call from that number (answered, or ringing longer than 12 seconds) is ignored too, so you can still phone each other.
- The located phone will ring briefly. Caller ID can be faked by a determined person, so this is convenience, not security.
- Needs Android 9 or newer.

## Offline behavior

- GPS works with no data or Wi-Fi. SMS needs cell signal, not internet.
- Received locations always show coordinates plus distance and compass direction from you, with no map.
- Map tiles only exist for areas you have viewed while online (they are cached). Open the map over your usual area on Wi-Fi before going off-grid.
- If a text cannot go out (no signal), the app tries again about a minute later.

## Notes

- Only texts from the number you entered are accepted, so strangers cannot move the pin.
- Google Play restricts apps that send SMS in the background, so install the APK directly.
- Each text is one SMS. The default limit is 50 per day (half of a 100 a day allowance, leaving room for your own texts). The app counts every text it sends, stops at the limit, and starts a new count at midnight (or after the number of calendar days you set). "Reset count" restarts it by hand. Texts you receive are not counted by the app.
- To save texts, after each interval the app sends only if you moved 50 m or more since the last text, with a "still here" text at least once an hour. Standing still costs at most 24 texts a day, and the limit stops sending when reached.
- Keep battery optimisation off for the app on phones that kill background services.
- iPhone does not allow apps to send texts automatically, so this works on Android only.
