# MemoryLink

A kiosk-locked Android tablet app for dementia/elderly users displaying calendar events and time.

> 🚧 **Work in Progress** - See [plan.md](./plan.md) for the detailed spec and roadmap.

## Overview

- **Memory User:** Sees large clock + next calendar event (read-only, no touch interaction)
- **Family Caregiver:** Manages events via shared Google Calendar from their phone
- **Configuration:** Remote settings via special `[CONFIG]` calendar events

## Key Features

- 📱 Full-screen kiosk mode (no escape for memory user)
- 🕐 Large, high-contrast clock always visible (72sp)
- 📅 Shows next calendar event for TODAY only
- 🌙 Configurable sleep mode (dimmed display at night)
- 🌅 Supports SUNRISE/SUNSET dynamic scheduling with offsets
- 📴 Works offline with 2-week event cache
- 🔐 Hidden admin access (5-tap + PIN)
- ⚙️ Remote configuration via special calendar events

## Configuration via Calendar Events

MemoryLink can be configured remotely by creating special calendar events with titles starting with `[CONFIG]`. This allows caregivers to adjust settings without physical access to the device.

### How It Works

1. **Create a calendar event** with a title like `[CONFIG] SLEEP 21:00`
2. **The app caches events 2 weeks in advance** - config events are processed immediately upon caching
3. **Settings persist** until overwritten by a newer config event of the same type, or manually changed in admin mode
4. **Config events are never shown** to the memory user - they're parsed and filtered out

### Best Practices

- **Schedule config events for tomorrow** (or any day within the next 2 weeks) to ensure they're cached and processed promptly
- **Config events can be deleted** from your calendar after the app has synced - the setting will remain active
- **Use recurring events sparingly** - a single config event sets the value permanently until changed

### Available Commands

| Command        | Example                    | Description                                          |
| -------------- | -------------------------- | ---------------------------------------------------- |
| `SLEEP`        | `[CONFIG] SLEEP 21:00`     | Set sleep mode start time (HH:MM)                    |
| `SLEEP`        | `[CONFIG] SLEEP SUNSET`    | Sleep at sunset                                      |
| `SLEEP`        | `[CONFIG] SLEEP SUNSET+30` | Sleep 30 minutes after sunset                        |
| `SLEEP`        | `[CONFIG] SLEEP SUNSET-15` | Sleep 15 minutes before sunset                       |
| `WAKE`         | `[CONFIG] WAKE 07:00`      | Set wake time (HH:MM)                                |
| `WAKE`         | `[CONFIG] WAKE SUNRISE`    | Wake at sunrise                                      |
| `WAKE`         | `[CONFIG] WAKE SUNRISE+15` | Wake 15 minutes after sunrise                        |
| `BRIGHTNESS`   | `[CONFIG] BRIGHTNESS 80`   | Screen brightness (0-100)                            |
| `FONT_SIZE`    | `[CONFIG] FONT_SIZE 48`    | Event title font size in sp (default: 48)            |
| `MESSAGE_SIZE` | `[CONFIG] MESSAGE_SIZE 60` | Message area % of screen height (20-80, default: 60) |
| `TIME_FORMAT`  | `[CONFIG] TIME_FORMAT 12`  | Clock format: `12` or `24` hour (default: 12)        |

### Example: Setting Up a Sleep Schedule

To have the display dim at 9 PM and wake at 7 AM:

1. Create event: `[CONFIG] SLEEP 21:00` (schedule for any time tomorrow)
2. Create event: `[CONFIG] WAKE 07:00` (schedule for any time tomorrow)
3. Wait for next sync (up to 5 minutes)
4. Optionally delete the events from your calendar

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

### Troubleshooting

| Issue                             | Solution                                                          |
| --------------------------------- | ----------------------------------------------------------------- |
| "Not allowed to set device owner" | Factory reset the device and skip Google account setup            |
| "Already has a device owner"      | Remove existing device owner first or factory reset               |
| App doesn't block home button     | Verify device owner is set with `adb shell dumpsys device_policy` |
| "Unknown admin component"         | Ensure the app is installed before running dpm command            |

### Without Device Owner

If device owner cannot be set, the app will still function but with reduced kiosk protection:

- Clock and events display normally
- Sleep mode and config parsing work
- Admin gesture (5-tap) works
- **BUT:** Home/back buttons will still work (user can exit)

For testing without device owner, the app logs: `Not device owner, skipping LockTask start`

## Project Status

- [x] Planning & Architecture
- [x] Phase 1: Project Setup
- [x] Phase 2: Display Layer (Kiosk UI)
- [x] Phase 3: State Machine
- [x] Phase 4: Calendar Integration
- [x] Phase 5: Config Parser
- [x] Phase 6: Admin Mode
- [x] Phase 7: Kiosk Lock
- [x] Phase 8: First-Time Setup
- [ ] Phase 9: Testing & Polish
- [ ] Phase 10: Documentation & Release

## License

MIT License - see [LICENSE](./LICENSE) for details.
