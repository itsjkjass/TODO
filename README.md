# PaceMate: an AI to-do planner that works at human speed (Android)

PaceMate takes the things you want to get done, breaks them into small steps, and
spreads those steps across your days so every deadline is met without overloading
you. A built-in logger times every task and notifies you when its time is up.

## What it does

1. **Onboarding interview.** On first launch the app asks about:
   - your **daily routine**: wake and sleep times, meals, gym, classes, family time. You can
     describe your day in plain words and Claude fills in the fields for you.
   - your **working hours**: start and end time, work days, commute.
   - your **preferences**: longest focus session, break length, when you have the most energy,
     how fast you usually work, how many task hours per day you're comfortable with,
     rest days, and how many days before a deadline you want to finish.
   - the **goals** you want to finish soon, each with a deadline.
2. **AI breakdown.** Claude splits each goal into concrete steps, each with a realistic
   estimate for an average person and an effort level (light, normal or deep work).
   You can edit, add or remove steps. Without an API key, an offline breakdown is used.
3. **Human-speed scheduling.** A deterministic planner (`core/Scheduler.kt`):
   - adjusts each estimate with your **personal pace factor**, adds a 15% planning cushion,
     adds 5 minutes to get started, and adds extra time for demanding work.
   - splits work into sessions no longer than your focus limit, with breaks between them.
   - only uses free time: never during sleep, the wake-up buffer, the wind-down before bed,
     work and commute, meals, or fixed commitments.
   - **spreads each goal evenly** over the days left before *deadline minus buffer*,
     instead of cramming it into the last few days.
   - never goes over your comfortable daily limit, or 70% of your free time.
   - puts deep work in your peak-energy hours.
   - tells you plainly when a deadline can't be met at a comfortable pace, and when it
     would realistically be done.
4. **Logger and notifications.** Press ▶ on any task to start its timer. You get:
   - an ongoing notification with a live countdown,
   - an alarm-style **"Time's up"** notification when the task's minutes run out, with
     *Done*, *+10 min* and *Stop* buttons. It fires even if the app is closed (exact alarms),
   - an optional reminder 2 minutes before each planned task starts.

   Every stretch of logged time is saved in the **Log** tab.
5. **It learns your real speed.** Each time you finish a task, the actual time you logged
   updates your pace factor, overall and per category (study, writing, coding...).
   Future plans then match how fast *you* really work.
6. **Missed something? No guilt.** Skipped and missed tasks are planned again on later days
   automatically.

## Screens

| Tab | Purpose |
|---|---|
| Today | Today's tasks, the running timer, how much of the day is done, warnings |
| Plan | Upcoming days, with a load bar against your comfortable limit |
| Goals | Goals, steps, progress, and adding or editing goals with AI breakdown |
| Log | Time actually spent per task, and your learned pace |
| Settings | API key and model, reminders, routine and preferences, re-plan, reset |

## Project layout

```
core/   Pure Kotlin/JVM planning engine (unit tested, no Android needed)
  Model.kt         data model (routine, preferences, goals, sessions, logs)
  PaceModel.kt     human-speed estimates and pace learning from the logger
  Scheduler.kt     turns goals into a stress-free calendar
  StateOps.kt      timer, logging, completion and re-planning rules
  ClaudePlanner.kt Claude integration (official Anthropic Java SDK, structured JSON output)
  Breakdown.kt     offline breakdown, routine parsing types, JSON config
app/    Android app (Jetpack Compose, Material 3)
  data/            JSON-file repository and settings
  timer/           TimerController, exact alarms, notifications, boot receiver
  ui/              onboarding, Today, Plan, Goals, Log, Settings screens
```

## Build and install

**Requirements:** Android Studio (Ladybug or newer) or JDK 17 plus the Android SDK (API 35).
The app runs on Android 8.0 (API 26) and up.

```bash
./gradlew :app:assembleDebug
```

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press **Run**.

Every push also builds the APK on GitHub Actions (`.github/workflows/android.yml`).
You can download it from the run's **Artifacts** section (`PaceMate-debug-apk`).

Run the planning-engine tests on their own (no Android SDK needed):

```bash
./gradlew -PcoreOnly :core:test
```

## AI setup

AI features use Claude through the official Anthropic SDK. In the first onboarding step
(or later in Settings), paste an API key from <https://console.anthropic.com>. The default
model is `claude-opus-5-5`, and you can change it in Settings. The key is stored only in the
app's private storage on the phone. Without a key, everything still works: routine entry is
manual and goals get a generic offline breakdown.

Notes:
- Requests ask for JSON that matches a schema (structured outputs). The request also turns
  on the server-side refusal fallback, so a request a safety classifier declines is retried
  on a suitable model instead of failing.
- For a production app, route requests through your own backend instead of putting the API
  key on the device.

## Permissions

- `POST_NOTIFICATIONS`: timer and reminder notifications. The app asks for it on Android 13+.
- `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`: lets the "time's up" alert fire on the minute.
  If exact alarms aren't allowed, the app falls back to inexact alarms.
- `RECEIVE_BOOT_COMPLETED`: restores the running timer and reminders after a reboot.
- `INTERNET`: Claude API calls.
