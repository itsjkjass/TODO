# PaceMate: an AI to-do planner that works at human speed (Android)

PaceMate takes the things you want to get done, breaks them into small steps, and
spreads those steps across your days so every deadline is met without overloading
you. A built-in logger times every task and notifies you when its time is up.

## What it does

1. **Chat about your day.** Instead of filling in a form, you chat with PaceMate. It asks one
   short question at a time (wake-up, bedtime, work hours, commute, energy, how much time you
   want for your own tasks, focus length), offers tap-to-answer suggestions, and fills in your
   routine live as you answer. With an AI key, Groq or Kimi run the conversation. Without one,
   a built-in offline interviewer understands loose answers like "7ish", "9 to 5", "weekdays"
   or "an hour". You can reopen the chat any time from Today or Settings.
   The onboarding interview also covers:
   - your **daily routine**: wake and sleep times, meals, gym, classes, family time. You can
     describe your day in plain words and the AI fills in the fields for you.
   - your **working hours**: start and end time, work days, commute.
   - your **preferences**: longest focus session, break length, when you have the most energy,
     how fast you usually work, how many task hours per day you're comfortable with,
     rest days, and how many days before a deadline you want to finish.
   - the **goals** you want to finish soon, each with a deadline.
2. **AI breakdown.** The AI (Groq or Kimi) splits each goal into concrete steps, each with a realistic
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
| Settings | AI provider, key and model, reminders, routine and preferences, re-plan, reset |

## Project layout

```
core/   Pure Kotlin/JVM planning engine (unit tested, no Android needed)
  Model.kt         data model (routine, preferences, goals, sessions, logs)
  PaceModel.kt     human-speed estimates and pace learning from the logger
  Scheduler.kt     turns goals into a stress-free calendar
  StateOps.kt      timer, logging, completion and re-planning rules
  AiPlanner.kt     Groq / Kimi integration (OpenAI-compatible chat API, JSON mode)
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

PaceMate works with two AI providers. Both use the same OpenAI-style chat API:

| Provider | Cost | Default model | Get a key |
|---|---|---|---|
| **Groq** (default) | Free tier, no card needed. Paid rates are a few cents per million tokens | `openai/gpt-oss-120b` (lighter option: `openai/gpt-oss-20b`) | <https://console.groq.com/keys> |
| **Kimi** (Moonshot) | Paid only, roughly $0.60+ per million input tokens | `kimi-k2.6` | <https://platform.moonshot.ai/console/api-keys> |

Pick the provider in the first onboarding step or in **Settings**, then paste the key. The model
name can be edited. If you get a "model not found" error, copy a current model name from your
provider's console. Each provider keeps its own key and model.

Without a key, everything still works: you enter your routine by hand and goals get a generic
offline breakdown. The key is stored only in the app's private storage on the phone. If you ever
publish the app, send requests through your own server instead of keeping the key on the device.

## Signing key (so updates keep your data)

Android only lets a new APK install over an old one when both were signed with the same key.
The key is **not** stored in this repository, because the repository is public and anyone with
the key could build an "update" your phone would accept. GitHub Actions reads it from two
repository secrets instead. Until you add them, builds still work, but each one is signed with a
new temporary key, so you have to uninstall before installing a newer build.

One-time setup (on a computer with Java or Android Studio installed):

1. Create the key. Choose a strong password and keep both the file and the password somewhere safe.
   If you lose the key, installed copies can't be updated any more.

   ```bash
   keytool -genkeypair -keystore pacemate.keystore -storetype PKCS12 -alias pacemate -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=PaceMate"
   ```

2. Convert it to text. On macOS or Linux:

   ```bash
   base64 -w0 pacemate.keystore > pacemate.b64
   ```

   On Windows (PowerShell):

   ```bash
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("pacemate.keystore")) > pacemate.b64
   ```

3. On GitHub, open the repository's **Settings → Secrets and variables → Actions → New repository
   secret** and add:
   - `PACEMATE_KEYSTORE_BASE64`: the full contents of `pacemate.b64`
   - `PACEMATE_KEYSTORE_PASSWORD`: the password from step 1
4. Re-run the latest workflow, or push any change. The build log shows
   "Using the fixed PaceMate signing key".

The first APK signed with the fixed key needs one last uninstall of any older PaceMate. After that,
every new build installs over it and keeps your data.

To sign local builds the same way, put the keystore in `app/` and create `app/keystore.properties`.
The file is git-ignored.

```
storeFile=pacemate.keystore
storePassword=YOUR_PASSWORD
keyAlias=pacemate
keyPassword=YOUR_PASSWORD
```

## Permissions

- `POST_NOTIFICATIONS`: timer and reminder notifications. The app asks for it on Android 13+.
- `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`: lets the "time's up" alert fire on the minute.
  If exact alarms aren't allowed, the app falls back to inexact alarms.
- `RECEIVE_BOOT_COMPLETED`: restores the running timer and reminders after a reboot.
- `INTERNET`: AI API calls (Groq or Kimi).
