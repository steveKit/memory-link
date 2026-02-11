# MemoryLink - Project Plan

> A kiosk-locked Android tablet app for dementia/elderly users displaying calendar events and time.

---

## 📋 Spec (Technical Requirements)

### User Stories

| Role             | Story                                                                               |
| ---------------- | ----------------------------------------------------------------------------------- |
| Memory User      | As a memory user, I see the current time and date at all times so I stay oriented.  |
| Memory User      | As a memory user, I see my next scheduled event clearly so I know what's coming up. |
| Memory User      | As a memory user, I cannot accidentally exit or break the app.                      |
| Family Caregiver | As a caregiver, I add events to a shared Google Calendar from my phone.             |
| Family Caregiver | As a caregiver, I configure sleep/wake times via special calendar events.           |
| Family Caregiver | As a caregiver, I can access admin settings via a hidden gesture + PIN.             |

### Functional Requirements

| ID    | Requirement                                                                      |
| ----- | -------------------------------------------------------------------------------- |
| FR-01 | Display current time (configurable 12/24-hour, 72sp bold) updating every minute. |
| FR-02 | Display current date (full: "Day, Month DD, YYYY") below time.                   |
| FR-03 | Display next calendar event title (48sp) and start time (32sp) when available.   |
| FR-04 | Show only TODAY's events; never show tomorrow's events.                          |
| FR-05 | Advance to next event when current event's start time passes.                    |
| FR-06 | Sync with Google Calendar API every 5 minutes.                                   |
| FR-07 | Parse `[CONFIG]` events for sleep/wake/brightness/time format settings.          |
| FR-08 | Support `SUNRISE`/`SUNSET` dynamic time with `+N`/`-N` minute offsets.           |
| FR-09 | Enter sleep mode (dimmed, clock only) during configured sleep period.            |
| FR-10 | Cache events 2 weeks in advance locally in Room database for offline resilience. |
| FR-11 | Hidden admin mode via 5-tap gesture + 4-digit PIN.                               |
| FR-12 | First-time setup wizard: PIN → Wi-Fi → Google sign-in → Calendar select.         |
| FR-13 | Kiosk lock: disable all touch in normal mode, block home/back.                   |
| FR-14 | Auto-launch on device boot.                                                      |

### Non-Functional Requirements

| ID     | Requirement                                                        |
| ------ | ------------------------------------------------------------------ |
| NFR-01 | Minimum 7:1 contrast ratio for all text.                           |
| NFR-02 | No animations except 300ms cross-fades.                            |
| NFR-03 | Battery: minimize wake-locks, use WorkManager for background sync. |
| NFR-04 | Offline: display last-known events if API unreachable.             |
| NFR-05 | Startup time < 3 seconds to clock display.                         |

---

## 🗂️ Architecture

### Tech Stack

| Component      | Technology                    |
| -------------- | ----------------------------- |
| Language       | Kotlin                        |
| UI             | Jetpack Compose               |
| DI             | Hilt                          |
| Async          | Coroutines + Flow             |
| Local DB       | Room                          |
| Secure Storage | EncryptedSharedPreferences    |
| Calendar API   | Google Calendar API v3        |
| Sunrise/Sunset | sunrise-sunset.org (free API) |
| Testing        | JUnit5, MockK, Maestro        |

### Module Structure

```
app/
├── src/main/
│   ├── java/com/memorylink/
│   │   ├── MainActivity.kt              # Single activity, hosts Compose
│   │   ├── MemoryLinkApp.kt             # Hilt application class
│   │   │
│   │   ├── di/                          # Hilt modules
│   │   │   ├── AppModule.kt
│   │   │   ├── DatabaseModule.kt
│   │   │   └── CalendarModule.kt
│   │   │
│   │   ├── data/                        # Data layer
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── EventDao.kt
│   │   │   │   └── EventEntity.kt
│   │   │   ├── remote/
│   │   │   │   ├── GoogleCalendarService.kt
│   │   │   │   └── SunriseSunsetApi.kt
│   │   │   └── repository/
│   │   │       ├── CalendarRepository.kt
│   │   │       └── SettingsRepository.kt
│   │   │
│   │   ├── domain/                      # Business logic
│   │   │   ├── model/
│   │   │   │   ├── CalendarEvent.kt
│   │   │   │   ├── DisplayState.kt
│   │   │   │   └── AppSettings.kt
│   │   │   ├── usecase/
│   │   │   │   ├── GetNextEventUseCase.kt
│   │   │   │   ├── ParseConfigEventUseCase.kt
│   │   │   │   └── DetermineDisplayStateUseCase.kt
│   │   │   └── StateCoordinator.kt      # Main state machine
│   │   │
│   │   ├── ui/                          # Presentation layer
│   │   │   ├── kiosk/                   # Main display screens
│   │   │   │   ├── KioskScreen.kt
│   │   │   │   ├── ClockDisplay.kt
│   │   │   │   ├── EventCard.kt
│   │   │   │   └── SleepOverlay.kt
│   │   │   ├── admin/                   # Admin mode screens
│   │   │   │   ├── AdminNavGraph.kt
│   │   │   │   ├── PinEntryScreen.kt
│   │   │   │   ├── SetupWizardScreen.kt
│   │   │   │   ├── WifiSettingsScreen.kt
│   │   │   │   ├── GoogleSignInScreen.kt
│   │   │   │   ├── CalendarSelectScreen.kt
│   │   │   │   ├── ManualConfigScreen.kt
│   │   │   │   └── TimezoneScreen.kt
│   │   │   ├── components/              # Shared UI components
│   │   │   │   ├── NumericKeypad.kt
│   │   │   │   └── LargeButton.kt
│   │   │   └── theme/
│   │   │       ├── Theme.kt
│   │   │       ├── Color.kt
│   │   │       └── Typography.kt
│   │   │
│   │   ├── service/                     # Background services
│   │   │   ├── CalendarSyncWorker.kt    # WorkManager periodic sync
│   │   │   └── BootReceiver.kt          # Auto-start on boot
│   │   │
│   │   └── util/
│   │       ├── ConfigParser.kt          # [CONFIG] event parser
│   │       ├── TimeUtils.kt
│   │       └── TouchInterceptor.kt      # Block touch in kiosk mode
│   │
│   ├── res/
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   └── themes.xml
│   │   └── xml/
│   │       └── device_admin_receiver.xml
│   │
│   └── AndroidManifest.xml
│
├── src/test/                            # Unit tests
│   └── java/com/memorylink/
│       ├── domain/
│       │   ├── ConfigParserTest.kt
│       │   ├── StateCoordinatorTest.kt
│       │   └── GetNextEventUseCaseTest.kt
│       └── data/
│           └── CalendarRepositoryTest.kt
│
└── src/androidTest/                     # Instrumented tests
    └── java/com/memorylink/
        └── ui/
            └── KioskScreenTest.kt
```

---

## 📅 Plan (Step-by-Step Roadmap)

### Phase 1: Project Setup

- [x] 1.1 Initialize Android project with Kotlin + Compose
- [x] 1.2 Configure Gradle with Hilt, Room, Coroutines dependencies
- [x] 1.3 Set up theme (dark background, high contrast colors, typography scale)
- [x] 1.4 Create base Hilt modules (AppModule, DatabaseModule)
- [x] 1.5 Create Room database schema for cached events

### Phase 2: Display Layer (Kiosk UI)

- [x] 2.1 Build `ClockDisplay` composable (72sp time, 36sp date)
- [x] 2.2 Build `EventCard` composable (48sp title, 32sp time)
- [x] 2.3 Build `KioskScreen` combining clock + event
- [x] 2.4 Build `SleepOverlay` for dimmed night mode
- [x] 2.5 Implement state-driven UI (AWAKE_NO_EVENT, AWAKE_WITH_EVENT, SLEEP)

### Phase 3: State Machine

- [x] 3.1 Define `DisplayState` sealed class
- [x] 3.2 Implement `StateCoordinator` with StateFlow
- [x] 3.3 Implement `DetermineDisplayStateUseCase` logic
- [x] 3.4 Implement `GetNextEventUseCase` (today only, next upcoming, 2-hour preview)
- [x] 3.5 Add 1-minute tick for state re-evaluation
- [x] 3.6 Write unit tests for state transitions (100% branch coverage)

### Phase 4: Calendar Integration

- [x] 4.1 Set up Google Cloud project + Calendar API credentials
- [x] 4.2 Implement OAuth2 sign-in flow with offline token storage
- [x] 4.3 Implement `GoogleCalendarService` (fetch today's events)
- [x] 4.4 Implement `CalendarRepository` (API + Room cache)
- [x] 4.5 Create `CalendarSyncWorker` (WorkManager, 5-min interval)
- [x] 4.6 Handle offline mode (use cached events)
- [x] 4.7 Write integration tests with mocked API responses

### Phase 5: Config Parser

- [x] 5.1 Implement `ConfigParser` for `[CONFIG]` event syntax
- [x] 5.2 Support SLEEP/WAKE static times (HH:MM)
- [x] 5.3 Support SUNRISE/SUNSET dynamic times
- [x] 5.4 Implement `SunriseSunsetApi` integration
- [x] 5.5 Support BRIGHTNESS, FONT_SIZE, MESSAGE_SIZE, TIME_FORMAT configs
- [x] 5.6 Store config in EncryptedSharedPreferences
- [x] 5.7 Write unit tests for all config patterns

### Phase 6: Admin Mode

- [x] 6.1 Implement 5-tap detection in top-left corner
- [x] 6.2 Build `PinEntryScreen` with numeric keypad
- [x] 6.3 Implement PIN storage + 3-attempt lockout
- [x] 6.4 Build admin navigation graph
- [ ] 6.5 Build `WifiSettingsScreen` (deferred to Phase 8 - Setup Wizard)
- [x] 6.6 Build `GoogleSignInScreen`
- [x] 6.7 Build `CalendarSelectScreen`
- [x] 6.8 Build `ManualConfigScreen` (override sleep/wake/brightness)
- [ ] 6.9 Build `TimezoneScreen` (deferred - device default used)
- [x] 6.10 Implement 5-minute inactivity auto-exit

### Phase 7: Kiosk Lock

- [ ] 7.1 Configure AndroidManifest for LockTaskMode
- [ ] 7.2 Create device_admin_receiver.xml
- [ ] 7.3 Implement `TouchInterceptor` to block touch in kiosk mode
- [ ] 7.4 Implement `BootReceiver` for auto-start
- [ ] 7.5 Add instructions for device owner provisioning (ADB command)

### Phase 8: First-Time Setup

- [ ] 8.1 Detect "no config" state on launch
- [ ] 8.2 Build `SetupWizardScreen` (PIN → Wi-Fi → Google → Calendar)
- [ ] 8.3 After setup complete, enter kiosk mode

### Phase 9: Testing & Polish

- [ ] 9.1 Maestro E2E flow: Setup → Event display → Config applied → Sleep mode
- [ ] 9.2 Test offline resilience (disable Wi-Fi, verify cached events show)
- [ ] 9.3 Test sleep/wake transitions at boundary times
- [ ] 9.4 Accessibility review (contrast checker, font sizes)
- [ ] 9.5 Battery profiling (ensure no excessive wake-locks)

### Phase 10: Documentation & Release

- [ ] 10.1 Write README with setup instructions
- [ ] 10.2 Document device owner provisioning steps
- [ ] 10.3 Create signed release APK
- [ ] 10.4 Test on physical tablet hardware

---

## 🔑 Key Decisions

| Decision          | Choice             | Rationale                                            |
| ----------------- | ------------------ | ---------------------------------------------------- |
| Calendar Provider | Google Calendar    | Free, reliable, zero hosting, family already uses it |
| Remote Config     | `[CONFIG]` events  | No server needed; family uses existing calendar app  |
| Kiosk Security    | 5-tap + PIN        | Simple for family, invisible to memory user          |
| Offline Strategy  | Room cache         | Guaranteed display even if Wi-Fi fails               |
| Sunrise/Sunset    | sunrise-sunset.org | Free, no API key, reliable                           |

---

## ⚠️ Risks & Mitigations

| Risk                                 | Mitigation                                               |
| ------------------------------------ | -------------------------------------------------------- |
| Google API rate limits               | 5-min polling = ~8,640/month (well under 1M limit)       |
| Device owner provisioning complexity | Document clear ADB commands; consider MDM for enterprise |
| Memory user discovers admin tap      | 5 taps in 2 seconds is unlikely for confused user        |
| Tablet battery drain                 | WorkManager respects Doze; sync only every 5 min         |
| OAuth token expiration               | Store refresh token; silent refresh on failure           |

---

## ✅ Definition of Done

The project is complete when:

1. Tablet displays time/date continuously without interaction
2. Events from shared Google Calendar appear automatically
3. `[CONFIG]` events successfully change sleep/wake times
4. Admin mode accessible only via hidden gesture + correct PIN
5. Memory user cannot exit the app or access other apps
6. Display continues to work when Wi-Fi is unavailable (cached events)
7. Maestro E2E test passes
8. README documents full setup process
