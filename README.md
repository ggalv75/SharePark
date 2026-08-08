# SharePark

**Never lose your car again — and never have to text someone where you parked.**

SharePark is an Android app that detects when you've parked by watching for your car's
Bluetooth to disconnect, captures the GPS location at that exact moment, reverse-geocodes it
to a street address, and can automatically share it over WhatsApp — all without you taking
the phone out of your pocket.

<p>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Architecture" src="https://img.shields.io/badge/architecture-MVVM%20%2B%20Clean-informational">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue">
</p>

---

## The problem

Parking apps make you remember to open them. That's exactly the moment you forget — you're
late, carrying groceries, on the phone. And when a partner or family member needs the car,
you end up describing a street corner from memory.

SharePark removes the human step entirely. The trigger is something that already happens
every time you park: your phone disconnects from the car's Bluetooth.

## How it works

```
Car Bluetooth disconnects
        │
        ▼
  BluetoothEventReceiver            matches MAC against registered vehicles
        │
        ▼
  ParkingDetectionService           foreground service (location + connectedDevice)
        │
        ├─ 10s debounce ──────────► still connected? → abort
        │                            (tunnel dropout, quick engine restart)
        ├─ high-accuracy GPS fix
        ├─ reverse geocode → address
        ├─ persist ParkingRecord (Room)
        ├─ notification with one-tap "Share"
        │
        ▼
  WhatsAppAutoSendService           optional, per-vehicle
        │
        ├─ phone unlocked?  → open chat, press send
        └─ phone locked?    → persist, fire on next unlock
```

The **debounce** step is what makes this usable in practice. A raw `ACL_DISCONNECTED` fires
on every brief signal drop; without the 10-second confirmation the app would spam you with
false "you parked!" notifications every time you drove through a tunnel.

Connectivity is re-checked through the **A2DP and HEADSET profile proxies**, not
`BluetoothManager.getConnectedDevices()` — the latter only supports GATT and throws
`IllegalArgumentException` for audio profiles. That distinction is the difference between
working detection and a crash loop on real hardware.

## Features

| | |
|---|---|
| 🚗 **Multi-vehicle** | Register any number of cars by pairing their Bluetooth device. Only the active one is monitored. |
| 📍 **Automatic capture** | GPS fix taken at the moment of disconnect, with accuracy recorded. |
| 🏠 **Address resolution** | Coordinates reverse-geocoded to a human-readable street address via the Google Geocoding API. |
| 🗺️ **Live map** | Compose-native Google Maps view of the current parking spot, with a full-screen mode. |
| 📜 **History** | Every parking event kept for 30 days, then pruned automatically by a WorkManager job. |
| 💬 **WhatsApp automation** | Per-vehicle rules — each car can target a different contact or group chat. |
| 🔒 **Deferred send** | If the phone is locked when you park, the message is queued and sent the moment you unlock. |
| 👥 **Trusted contacts** | Saved recipients for quick manual sharing. |
| 🔁 **Survives reboot** | `BootReceiver` re-arms monitoring after restart or app update. |
| 🌐 **RTL-first** | Hebrew UI with full right-to-left layout support. |

## WhatsApp automation

Two modes, configured per vehicle:

- **Contact** — builds a `wa.me` deep link with the message pre-filled and opens the chat directly.
- **Group** — opens WhatsApp's share picker and locates the target group by name, scrolling
  the chat list if needed.

In both cases an `AccessibilityService` presses the send button. Android requires the user to
enable that service by hand under **Settings → Accessibility**, and that explicit grant *is*
the permission model for the feature. The service is scoped to WhatsApp only and does nothing
but complete a send the user already configured.

If the screen is off or locked when parking is detected — the common case, phone in pocket —
the send is written to `PendingAutomationStore` and replayed on `ACTION_USER_PRESENT`. A queued
send also survives the process being killed: the service re-checks the store on reconnect.

## Architecture

Clean Architecture over MVVM, with a strict one-way dependency flow:

```
ui/          Compose screens + ViewModels, state exposed as StateFlow
  ├── screens/{map, vehicles, history, settings}
  ├── navigation/    single NavHost, bottom-bar destinations
  ├── components/    shared UI primitives
  └── theme/         Material 3 theming

domain/      Framework-free business logic
  ├── model/         Vehicle, ParkingRecord, TrustedContact
  └── usecase/       SaveParking, ShareLocation, CleanupOldRecords, WhatsAppLinkBuilder

data/        Persistence and remote access
  ├── local/         Room database (v3), DAOs, entities, DataStore prefs
  ├── remote/        Retrofit Geocoding client
  └── repository/    single source of truth per aggregate

platform/    Android framework integration
  ├── bluetooth/     ACL connect/disconnect receiver
  ├── service/       ParkingDetectionService, BootReceiver
  ├── location/      fused location access
  ├── automation/    WhatsApp accessibility service + pending-send store
  ├── notification/  channels, actions, share trampoline
  ├── permissions/   runtime permission orchestration
  └── worker/        WorkManager cleanup job

di/          Hilt modules
```

`domain` has no Android imports. `ui` never touches `data` directly. `platform` is the only
layer that knows about services, receivers, and system intents — which keeps the detection
pipeline testable in isolation from Compose.

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 |
| DI | Hilt 2.51.1 (incl. `hilt-work`) |
| Persistence | Room 2.6.1, DataStore Preferences |
| Async | Coroutines 1.9.0 + Flow |
| Maps | Maps Compose 6.1.0, Play Services Maps & Location |
| Networking | Retrofit 2.11.0 + Gson, OkHttp logging |
| Background | WorkManager 2.9.1, foreground services |
| Build | Gradle KTS, AGP 8.5.2, version catalog, KSP |

## Getting started

### Prerequisites

- Android Studio Ladybug or newer
- JDK 17
- A physical Android device (Bluetooth and GPS behaviour cannot be tested reliably on an emulator)
- A Google Maps Platform API key with **Maps SDK for Android** and **Geocoding API** enabled

### Setup

```bash
git clone https://github.com/<your-username>/SharePark.git
cd SharePark
cp local.properties.example local.properties
```

Then edit `local.properties`:

```properties
sdk.dir=C\:\\Android
MAPS_API_KEY=your_key_here
```

The key is injected as both a manifest placeholder and a `BuildConfig` field at build time, so
it never appears in source. `local.properties` is git-ignored.

Enable the secret-guard hook once per clone:

```bash
git config core.hooksPath .githooks
```

It rejects any commit that would stage `local.properties`, a signing keystore, or an
`AIzaSy…` literal. CI enforces the same rule on `main`. See [SECURITY.md](SECURITY.md) for the
full key-handling model, including how to restrict the key in the Google Cloud Console.

### Build

```bash
./gradlew assembleDebug        # macOS / Linux
.\gradlew.bat assembleDebug    # Windows
```

### First run

1. Grant location (**Allow all the time** — required for background detection), Bluetooth, and notification permissions.
2. Add a vehicle and select its paired Bluetooth device.
3. Optionally exempt the app from battery optimisation so detection survives Doze.
4. To enable auto-send: **Settings → WhatsApp automation**, pick a target per vehicle, then enable the accessibility service when prompted.

## Permissions and why each is needed

| Permission | Reason |
|---|---|
| `ACCESS_FINE_LOCATION` | Capture the parking coordinates. |
| `ACCESS_BACKGROUND_LOCATION` | Detection happens with the app closed and the phone in your pocket. |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` | Read paired devices and observe connection state. `neverForLocation` is declared on scan. |
| `FOREGROUND_SERVICE_LOCATION` / `_CONNECTED_DEVICE` | Required service types for the detection service. |
| `POST_NOTIFICATIONS` | Deliver the "parking saved" notification and its share action. |
| `RECEIVE_BOOT_COMPLETED` | Re-arm monitoring after a reboot. |
| `INTERNET` | Reverse geocoding only. |
| `WAKE_LOCK` | Complete an automated send that fires while the screen is off. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional, user-initiated; prevents Doze from delaying detection. |

## Privacy

Parking history lives in a local Room database on the device. There is no backend, no
analytics, and no account. The only outbound network call is reverse geocoding to Google, which
receives a coordinate pair and returns an address. Location is shared exactly once per parking
event, to the recipient you configured yourself. Records older than 30 days are deleted
automatically.

## Roadmap

- [ ] Unit tests for the detection pipeline and `WhatsAppLinkBuilder` normalisation
- [ ] English localisation alongside the existing Hebrew strings
- [ ] Parking-duration tracking and meter-expiry reminders
- [ ] Photo attachment for the parking spot (level, bay number)
- [ ] Wear OS companion tile

## License

MIT — see [LICENSE](LICENSE).
