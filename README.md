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
- 🌅 Supports SUNRISE/SUNSET dynamic scheduling
- 📴 Works offline with cached events
- 🔐 Hidden admin access (5-tap + PIN)

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

## Project Status

- [x] Planning & Architecture
- [x] Phase 1: Project Setup
- [ ] Phase 2: Display Layer (Kiosk UI)
- [ ] Phase 3: State Machine
- [ ] Phase 4: Calendar Integration
- [ ] Phase 5: Config Parser
- [ ] Phase 6: Admin Mode
- [ ] Phase 7: Kiosk Lock
- [ ] Phase 8: First-Time Setup
- [ ] Phase 9: Testing & Polish
- [ ] Phase 10: Documentation & Release

## License

MIT License - see [LICENSE](./LICENSE) for details.
