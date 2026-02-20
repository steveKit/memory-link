# MemoryLink

MemoryLink is a Kotlin-based kiosk application that transforms any Android tablet into a distraction-free "smart clock" for seniors and individuals with cognitive challenges.

By stripping away complex interfaces and focusing only on what's coming next, MemoryLink provides a clear, reliable way for users to stay oriented. Meanwhile, it allows family members to manage the daily schedule remotely through a shared Google Calendar.

## Key Features

- 📱 **Full-Screen Kiosk Mode:** Locks the device down so the memory user cannot accidentally exit the app.
- 🕐 **High-Contrast Display:** Features a large clock, date, and the next calendar event with auto-scaling text.
- 📅 **Remote Management:** Caregivers manage events and device settings from their phone via Google Calendar.
- 🌙 **Sleep Schedule:** Configurable night mode dims the display automatically.
- 📴 **Offline Resilience:** Works offline with a 2-week rolling event cache.
- 🔐 **Secure Admin Access:** Hidden panel accessed via a 5-tap gesture + PIN.

---

## 🛠 Developer Guide: Setup & Installation

MemoryLink requires specific provisioning to function as a true, inescapable kiosk.

### Tech Stack

| Component | Technology        | Component | Technology             |
| --------- | ----------------- | --------- | ---------------------- |
| Language  | Kotlin            | Local DB  | Room                   |
| UI        | Jetpack Compose   | Calendar  | Google Calendar API v3 |
| DI        | Hilt              | Testing   | JUnit5, MockK, Maestro |
| Async     | Coroutines + Flow |           |                        |

### Device Owner Provisioning (Kiosk Mode Setup)

For full kiosk functionality (blocking the home button, recent apps, and notifications), the app must be set as the **Device Owner**.

**Prerequisites:**

- Android device running Android 8.0 (API 26) or higher.
- USB debugging enabled via ADB.
- The device must be factory reset or have no Google accounts set up.

**Steps:**

1. Factory reset the tablet (Settings → System → Reset options → Erase all data).
2. Skip Google account setup during the initial device setup screen.
3. Enable Developer Options: Tap "Build number" 7 times, then enable "USB debugging".
4. Install the app via ADB:
   ```bash
   adb install app-release.apk
   ```
5. Set the app as Device Owner:
   ```bash
   adb shell dpm set-device-owner com.memorylink/.service.DeviceAdminReceiver
   ```
   (Success will output: `Success: Device owner set to package com.memorylink`)
6. Launch the app. It will now run in full kiosk mode.

> **Note on Non-Kiosk Development:** If you run the app without Device Owner permissions, it will function normally but lack system-level navigation blocking. The logs will output: `Not device owner, skipping LockTask start`. To remove Device Owner status later, use `adb shell dpm remove-active-admin com.memorylink/.service.DeviceAdminReceiver`.

### Google Calendar API Scopes

During the in-app Google Sign-In setup (accessed via Admin Mode), the app requests:

- `calendar.readonly`: To list calendars and read events.
- `calendar.events`: To automatically delete `[CONFIG]` events once successfully processed.

---

## 📱 User Guide: Caregiver Management

To access the device's Admin Mode, tap 5 times rapidly in the top-left corner of the screen and enter your 4-digit PIN. (The panel times out after 30 seconds of inactivity).

### 1. Formatting Calendar Events

Events are fetched automatically (every 15 minutes) and displayed in a grammatically natural way for the user.

> [!IMPORTANT]
> **Language & Grammar:** Display phrases (e.g., "Today is...", "At 10:30 am...") are hard-coded in English. Name your calendar events as nouns or phrases to ensure natural readability.

**Best Practices:**

- ✅ **Do:** "Mom's Birthday", "Doctor Appointment", "Lunch with Sarah"
- ❌ **Don't:** "Today is Mom's Birthday" (displays redundantly as "Today is Today is Mom's Birthday")
- ❌ **Don't:** Add ending punctuation for multi-day events.

**Display Logic Examples:**

| Event Type | Scenario            | How it Displays on Screen                |
| ---------- | ------------------- | ---------------------------------------- |
| Timed      | Today               | "At 10:30 am, Doctor Appointment"        |
| Timed      | Tomorrow            | "Tomorrow, at 2:00 pm, Lunch with Sarah" |
| All-Day    | Single-day today    | "Today is Mom's Birthday"                |
| All-Day    | Multi-day (ongoing) | "Vacation until Friday"                  |

### 2. Remote Configuration `[CONFIG]`

Caregivers can adjust device settings remotely without physical access to the tablet. Create a calendar event with a title starting with `[CONFIG]`.

- **Caching:** The app processes config events immediately upon caching (it looks 2 weeks ahead).
- **Visibility:** Config events are never shown to the memory user.
- **Auto-Cleanup:** Successfully processed config events are immediately deleted from your Google Calendar. If a config event stays in your calendar, the syntax is invalid.

**Available Commands:**

| Command           | Example                      | Description                                            |
| ----------------- | ---------------------------- | ------------------------------------------------------ |
| SLEEP             | `[CONFIG] SLEEP 21:00`       | Set sleep mode start time (24h or 12h format).         |
| WAKE              | `[CONFIG] WAKE 7:00`         | Set wake time (24h or 12h format).                     |
| BRIGHTNESS        | `[CONFIG] BRIGHTNESS 80`     | Screen brightness (0-100).                             |
| TIME_FORMAT       | `[CONFIG] TIME_FORMAT 12`    | Clock format: `12` or `24` hour.                       |
| SHOW YEAR         | `[CONFIG] SHOW YEAR`         | Display year in date (e.g., "February 11, 2026").      |
| HIDE YEAR         | `[CONFIG] HIDE YEAR`         | Hide year in date (e.g., "February 11").               |
| SHOW SLEEP_EVENTS | `[CONFIG] SHOW SLEEP_EVENTS` | Show next event during sleep mode.                     |
| HIDE SLEEP_EVENTS | `[CONFIG] HIDE SLEEP_EVENTS` | Hide events during sleep (clock only, default).        |
| SHOW HOLIDAYS     | `[CONFIG] SHOW HOLIDAYS`     | Show holiday calendar events (if configured in Admin). |
| HIDE HOLIDAYS     | `[CONFIG] HIDE HOLIDAYS`     | Hide holiday calendar events (if configured in Admin). |

> **Note:** If using 12-hour format for schedules, WAKE assumes AM and SLEEP assumes PM. 24-hour format is recommended for clarity.

---

## License

MIT License - see [LICENSE](LICENSE) for details.
