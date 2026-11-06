# Nota

An Android 14+ reminder app where each reminder's schedule **and** the way it reminds you are fully configurable.
Built with Kotlin and Jetpack Compose. No accounts, no network; reminders are stored on the device.

## What it does

**Schedules:** one time, daily, weekly (pick days), monthly, yearly, each with "every N" (every 3 days, every other week, every 3 months...).
Monthly reminders on the 29th–31st fall on the last day of shorter months; Feb 29 yearly reminders fall on Feb 28 in non-leap years.

**Behaviours (each one is a separate toggle, per reminder):**

| Option | What happens |
|---|---|
| Alert: Sound + vibrate / Vibrate only / Silent | How the notification gets your attention |
| Stay in the notification bar until Done | The notification can't be cleared except with **Done** or **Snooze**. If you swipe it away it immediately comes back (see the Android 14 note below). |
| Keep alerting until Done (every N minutes) | Re-alerts on a timer until you tap Done |
| Full-screen alarm (after N minutes) | If it's still not done after N minutes (or right away), a full-screen alarm takes over and rings/vibrates until you tap Done or Snooze |
| Snooze length | What the Snooze button does |

The editor shows a plain-English **"What will happen"** summary of the exact schedule and behaviour, plus when the first reminder will fire.
"Save and try it now" fires it immediately so you can feel what it's like.

## Android 14 notes

- **Unswipeable notifications:** since Android 14, apps can no longer stop users swiping away "ongoing" notifications while the phone is unlocked.
  The app works around this by watching for the swipe and re-posting the notification instantly (quietly). It still can't be cleared without Done/Snooze.
- **Exact alarms:** uses `USE_EXACT_ALARM`, which is auto-granted for alarm/reminder apps. If it's ever unavailable, the app shows a warning card and falls back to slightly inexact timing.
- **Full-screen alarms:** Android 14 makes `USE_FULL_SCREEN_INTENT` a special permission. If you turn on a full-screen alarm and it isn't allowed, the main screen shows a card that opens the right Settings page.
  Android only shows full-screen alarms over the lock screen / when the screen is off; if you're actively using the phone it appears as a pop-up notification instead.
- **Notifications** permission is requested from the main screen.
- Reminders are restored after reboot, app update, and time/time-zone changes. A reminder missed while the phone was off fires once at boot.
- Some phone makers (Samsung, Xiaomi, OnePlus...) aggressively kill background apps. If reminders are late, set the app's battery usage to "Unrestricted".

## Building

- **Android Studio:** open this folder, let Gradle sync, press Run. Requires Android SDK 35.
- **Command line:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- **GitHub:** pushing this repo runs `.github/workflows/build.yml`, which runs the unit tests and uploads a debug APK as a build artifact you can download and install.

## Code map

- `data/Reminder.kt` – the reminder + behaviour model
- `data/Recurrence.kt` – computes the next occurrence (unit-tested in `app/src/test`)
- `data/ReminderStore.kt` – JSON file storage exposed as a `StateFlow`
- `alarm/ReminderEngine.kt` – every state change (fire, nag, full-screen, done, snooze, swipe, restore) and the AlarmManager alarms
- `alarm/Notifier.kt` – notification channels and the notification itself
- `ui/` – Compose screens: reminder list, editor, full-screen alarm
