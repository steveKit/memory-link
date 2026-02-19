# MemoryLink

MemoryLink is a Kotlin-based kiosk application that transforms any Android tablet into a distraction-free "smart clock" for seniors and individuals with cognitive challenges. By replacing complex mobile interfaces with a high-contrast, read-only display, MemoryLink provides a single, reliable source of truth for the time, date, and "what’s happening next."

## Why MemoryLink?

- **Cognitive Clarity:** Strips away the noise of modern OS notifications and icons, leaving only essential information.
- **Locked-Down Security:** Utilizes Android Kiosk mode to prevent accidental navigation or "getting lost" in other apps.
- **Remote Peace of Mind:** Caregivers manage the schedule via Google Calendar; the app handles the rest.

> [!IMPORTANT]
> **Language & Grammar:** This app is currently optimized for English sentence structure. Display phrases (e.g., "Today is...", "At 10:30 am...") are hard-coded with English syntax to ensure natural readability for the user.

## Overview

- **Memory User:** Sees large clock + next calendar event (read-only, no touch interaction)
- **Family Caregiver:** Manages events via shared Google Calendar from their phone
- **Configuration:** Remote settings via special `[CONFIG]` calendar events

## Key Features

- 📱 Full-screen kiosk mode (no escape for memory user)
- 🕐 Large, high-contrast clock with 3-line layout (time / day & date / year)
- 📅 Shows next calendar event for TODAY only
- 🌙 Configurable sleep mode (dimmed display at night)
- 📴 Works offline with 2-week event cache
- 🔐 Hidden admin access (5-tap + PIN)
- ⚙️ Remote configuration via special calendar events

## Event Display

The display shows calendar events with clear, auto-scaling text optimized for elderly/visually impaired users.

### Display Language Examples

**Timed Events (event card area):**

| Scenario                | Display                                                     |
| ----------------------- | ----------------------------------------------------------- |
| Today                   | "At 10:30 am, Doctor Appointment"                           |
| Tomorrow                | "Tomorrow, at 2:00 pm, Lunch with Sarah"                    |
| Future (within 2 weeks) | "On Wednesday, February 18th at 10:30 am, Physical Therapy" |

**All-Day Events (clock area):**

| Scenario            | Display                      |
| ------------------- | ---------------------------- |
| Single-day today    | "Today is Mom's Birthday"    |
| Single-day tomorrow | "Tomorrow is Family Reunion" |
| Future day          | "Wednesday is Road Trip"     |
| Multi-day (ongoing) | "Vacation until Friday"      |

### Formatting Tips for Caregivers

Since event titles are inserted into grammatical phrases, follow these guidelines:

- ✅ **Name events as nouns/phrases:** "Mom's Birthday", "Doctor Appointment", "Family Reunion"
- ❌ **Avoid starting with "Today is":** "Today is Mom's Birthday" → displays as "Today is Today is Mom's Birthday"
- ❌ **Avoid ending punctuation for multi-day events:** "European Vacation." → displays as "European Vacation. until Friday"

## Configuration via Calendar Events

MemoryLink can be configured remotely by creating special calendar events with titles starting with `[CONFIG]`. This allows caregivers to adjust settings without physical access to the device.

### How It Works

1. **Create a calendar event** with a title like `[CONFIG] SLEEP 21:00`
2. **The app caches events 2 weeks in advance** - config events are processed immediately upon caching
3. **Config events are never shown** to the memory user - they're parsed and filtered out
4. **Successfully processed config events are automatically deleted** from the calendar

### Config Event Consumption

When a `[CONFIG]` event is processed:

- ✅ **Valid syntax:** Settings are applied, event is deleted from Google Calendar
- ❌ **Invalid syntax:** Event remains in calendar (serves as a visual indicator of the error)

If a config event stays in your calendar after sync, check the syntax - the app could not parse it.

### Time Format in Config Events

The app accepts both 12-hour and 24-hour formats for SLEEP and WAKE times:

| Format                 | Example                | Notes                                 |
| ---------------------- | ---------------------- | ------------------------------------- |
| 24-hour                | `[CONFIG] SLEEP 21:00` | Recommended for clarity               |
| 12-hour **(no AM/PM)** | `[CONFIG] WAKE 7:00`   | **WAKE assumes AM, SLEEP assumes PM** |

### Best Practices

- **Schedule config events ahead of the current date** to ensure they're cached and processed promptly
- **Config events are auto-deleted** from your calendar after being processed. This indicates that they have been applied.

### Available Commands

| Command             | Example                      | Description                                      |
| ------------------- | ---------------------------- | ------------------------------------------------ |
| `SLEEP`             | `[CONFIG] SLEEP 21:00`       | Set sleep mode start time (HH:MM or 12h format)  |
| `WAKE`              | `[CONFIG] WAKE 7:00`         | Set wake time (HH:MM or 12h format)              |
| `BRIGHTNESS`        | `[CONFIG] BRIGHTNESS 80`     | Screen brightness (10-100)                       |
| `TIME_FORMAT`       | `[CONFIG] TIME_FORMAT 12`    | Clock format: `12` or `24` hour (default: 12)    |
| `SHOW YEAR`         | `[CONFIG] SHOW YEAR`         | Display year in date (e.g., "February 11, 2026") |
| `HIDE YEAR`         | `[CONFIG] HIDE YEAR`         | Hide year in date (e.g., "February 11")          |
| `SHOW SLEEP_EVENTS` | `[CONFIG] SHOW SLEEP_EVENTS` | Show next event during sleep mode                |
| `HIDE SLEEP_EVENTS` | `[CONFIG] HIDE SLEEP_EVENTS` | Hide events during sleep (clock only, default)   |
| `SHOW HOLIDAYS`     | `[CONFIG] SHOW HOLIDAYS`     | Show holiday calendar events\*                   |
| `HIDE HOLIDAYS`     | `[CONFIG] HIDE HOLIDAYS`     | Hide holiday calendar events\*                   |

_\*HOLIDAYS commands only apply if a holiday calendar is configured in admin mode._

### Example: Setting Up a Sleep Schedule

To have the display dim at 9 PM and wake at 7 AM:

1. Create event: `[CONFIG] SLEEP 9:00` (schedule for any time tomorrow)
2. Create event: `[CONFIG] WAKE 7:00` (schedule for any time tomorrow)
3. Wait for next sync (up to 15 minutes)
4. Events will be auto-deleted from your calendar once processed

## Calendar Sync

The app syncs with Google Calendar automatically to fetch new events.

### Sync Schedule

| Sync Type    | Frequency        | Purpose                                     |
| ------------ | ---------------- | ------------------------------------------- |
| **Primary**  | Every 15 minutes | Main sync via foreground service            |
| **Backup**   | Once daily       | Safety net if service is interrupted        |
| **Manual**   | On-demand        | "Sync Now" button in admin panel            |
| **Holidays** | Once weekly      | Active only if holiday calendar is selected |

## Google Calendar API Scopes

The app requires two OAuth scopes:

| Scope               | Purpose                        |
| ------------------- | ------------------------------ |
| `calendar.readonly` | List calendars and read events |
| `calendar.events`   | Delete processed config events |

Both scopes are requested during Google Sign-In setup.

## Admin Mode

Access admin mode by tapping 5 times rapidly in the top-left corner of the screen, then entering your 4-digit PIN.

### Admin Features

- Google account sign-in
- Calendar selection
- Holiday calendar selection
- Display settings (wake/sleep times, brightness, time format)
- Manual sync trigger
- Exit kiosk mode

### Admin Timeout

The admin panel automatically returns to kiosk mode after **30 seconds of inactivity**.

## Tech Stack

| Component | Technology             |
| --------- | ---------------------- |
| Language  | Kotlin                 |
| UI        | Jetpack Compose        |
| DI        | Hilt                   |
| Async     | Coroutines + Flow      |
| Local DB  | Room                   |
| Calendar  | Google Calendar API v3 |
| Testing   | JUnit5, MockK, Maestro |

## Kiosk Mode Setup (Device Owner Provisioning)

For full kiosk functionality (blocking home button, recent apps, etc.), the app must be set as **Device Owner**. This requires a factory-reset device or a freshly set up device.

### Prerequisites

- Android device running Android 8.0 (API 26) or higher
- USB debugging enabled
- ADB installed on your computer
- Device must be **factory reset** OR have **no Google accounts** set up

### Setup Steps

1. **Factory reset the tablet** (Settings → System → Reset options → Erase all data)

2. **Skip Google account setup** during initial device setup (tap "Skip" or "Set up later")

3. **Enable Developer Options:**
   - Go to Settings → About tablet
   - Tap "Build number" 7 times
   - Go back to Settings → System → Developer options
   - Enable "USB debugging"

4. **Install the app via ADB:**

   ```bash
   adb install app-release.apk
   ```

5. **Set the app as Device Owner:**

   ```bash
   adb shell dpm set-device-owner com.memorylink/.service.DeviceAdminReceiver
   ```

   You should see: `Success: Device owner set to package com.memorylink`

6. **Launch the app** - it will now run in full kiosk mode

### Verifying Device Owner Status

To check if the app is set as device owner:

```bash
adb shell dumpsys device_policy | grep "Device Owner"
```

### Removing Device Owner (for development)

To remove device owner status and allow normal device operation:

```bash
adb shell dpm remove-active-admin com.memorylink/.service.DeviceAdminReceiver
```

Or from the device: Admin Mode → Settings → Exit Kiosk Mode (requires factory reset for full removal).

### Without Device Owner

The app will still function but with reduced kiosk protection:

- Clock and events display normally
- Sleep mode and config parsing work
- Admin gesture (5-tap) works
- **BUT:** Home/back buttons will still work (user can exit)

For testing without device owner, the app logs: `Not device owner, skipping LockTask start`

## License

MIT License - see [LICENSE](./LICENSE) for details.
