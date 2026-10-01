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
| 🚗 **Multi-vehicle** | Register any number of cars by their paired Bluetooth device. Every registered car is monitored, and the disconnecting MAC address identifies which one just parked. |
| 📍 **Automatic capture** | GPS fix taken at the moment of disconnect, with accuracy recorded. |
| 🏠 **Address resolution** | Coordinates reverse-geocoded to a human-readable street address via the Google Geocoding API. |
| 🗺️ **Live map** | Compose-native Google Maps view of the current parking spot, with a full-screen mode. |
| 📜 **History** | Every parking event kept for 30 days, then pruned automatically by a WorkManager job. |
| 💬 **WhatsApp automation** | Per-vehicle rules — each car can message a different trusted contact. |
| 🎯 **Automation zones** | Mark addresses on the map with a radius each; the automated message only fires for parkings inside one of them. |
| 🔒 **Deferred send** | If the phone is locked when you park, the message is queued and sent the moment you unlock. |
| 👥 **Trusted contacts** | Saved recipients for quick manual sharing. |
| 🤝 **Shared vehicles** | Several people share one car and all see, live, where it was last parked and by whom. |
| 🔁 **Survives reboot** | The Bluetooth receiver is declared in the manifest, so detection keeps working after a restart or app update; `BootReceiver` recreates the notification channels. |
| 🌐 **RTL-first** | Hebrew UI with full right-to-left layout support. |

## WhatsApp automation

Each vehicle points at one trusted contact. A parking builds a `wa.me` deep link with the
message pre-filled, which opens that contact's chat directly, and an `AccessibilityService`
presses send. Android requires the user to enable that service by hand under
**Settings → Accessibility**, and that explicit grant *is* the permission model for the
feature. The service is scoped to WhatsApp only, acts on nothing but the send button of a
conversation the app opened itself, and does nothing else.

Group chats are deliberately not supported. WhatsApp exposes no addressable link for a group,
so the only route is driving its share picker by simulating taps and matching the chat by its
rendered name — a flow whose screens and view ids change between WhatsApp builds, and whose
failure mode is posting a private location to the wrong chat. That risk isn't worth the
feature.

If the screen is off or locked when parking is detected — the common case, phone in pocket —
the send is written to `PendingAutomationStore` and replayed on `ACTION_USER_PRESENT`. A queued
send also survives the process being killed: the service re-checks the store on reconnect.

The outcome of every attempt is recorded in `AutomationStatusStore` and surfaced in the
automation screen — an unattended flow that fails silently is otherwise indistinguishable from
one that never ran. The same screen has a per-vehicle **בדיקה** button that runs the real
send path with a test message, so the flow can be verified without driving anywhere.

## Automation zones

Automation is gated on *where* the car parked. Each zone is an address (searched by text or
tapped on the map) plus a radius of 50–500 m, and a parking only triggers a message when it
falls inside an enabled zone — the smallest matching one wins, so a tight circle drawn inside
a wider one is the one reported. With no zones defined the gate is inert and automation runs
everywhere, so enabling the option can never silently switch the feature off.

## Shared vehicles

A car can be shared between several people. Say Shani and Yoav drive the same car: whoever
parks it, the other sees the new spot on their map within seconds, with a notification naming
who parked.

- **Accounts.** Sharing needs a Google sign-in (**Settings → account**). Nothing else in the app
  does. Firebase Auth handles the account and Firestore holds the shared data.
- **Share.** On the vehicles screen, the share button on a car creates its cloud copy and an
  8-character invite code, valid for 48 hours.
- **Join.** The other person taps **join** in the vehicles screen's top bar and enters the code.
  If they already registered the same car with its Bluetooth, they link to it and their phone
  detects parkings too. If not, they add it as **view-only**: they see where it is, but their
  phone never detects it.
- **Sync.** A detected parking on a shared car is uploaded to
  `vehicles/{id}/parkings`. `SharedParkingSync` listens to every shared car and writes incoming
  parkings into the same Room tables, so the map, history and notifications need no special
  handling. Each parking carries a key so a phone doesn't re-import its own upload.
- **Access control.** [`firestore.rules`](firestore.rules) limits a car and its parkings to its
  members. Joining needs no server code: the joiner may only add their own uid, and only while
  citing an unexpired invite for that car. The last member to leave deletes the car and its
  history.

"Live" means the parking spot, not live tracking while driving. The cloud keeps the same 30-day
history as the device.

## Architecture

Clean Architecture over MVVM, with a strict one-way dependency flow:

```
ui/          Compose screens + ViewModels, state exposed as StateFlow
  ├── screens/{map, vehicles, history, settings}
  ├── navigation/    single NavHost, bottom-bar destinations
  ├── components/    shared UI primitives
  └── theme/         Material 3 theming

domain/      Framework-free business logic
  ├── model/         Vehicle, ParkingRecord, TrustedContact, AutomationZone
  └── usecase/       SaveParking, ShareLocation, CleanupOldRecords, WhatsAppLinkBuilder

data/        Persistence and remote access
  ├── local/         Room database (v5), DAOs, entities, DataStore prefs
  ├── remote/        Retrofit Geocoding client; cloud/ = Firebase auth + shared vehicles
  └── repository/    single source of truth per aggregate

platform/    Android framework integration
  ├── bluetooth/     ACL connect/disconnect receiver
  ├── service/       ParkingDetectionService, BootReceiver
  ├── location/      fused location access
  ├── automation/    WhatsApp accessibility service + pending-send store
  ├── sync/          SharedParkingSync — cloud parkings of shared cars → Room
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
| Cloud (shared vehicles) | Firebase Auth + Firestore (BoM 33.5.1), Credential Manager for Google sign-in |
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

### Firebase (optional, for shared vehicles)

Without this the app builds and runs normally; the account card and the share/join buttons
simply don't appear.

1. Create a project in the [Firebase console](https://console.firebase.google.com) and add an
   Android app with package name `com.sharepark`.
2. Add your debug SHA-1 under the app's settings (needed for Google sign-in):
   `./gradlew signingReport`.
3. **Authentication → Sign-in method**: enable **Google**.
4. **Firestore Database**: create a database.
5. Download `google-services.json` into `app/`. It is git-ignored like `local.properties`.
6. Deploy the security rules (needs the [Firebase CLI](https://firebase.google.com/docs/cli)):

```bash
firebase deploy --only firestore:rules --project <your-project-id>
```

### Build

```bash
./gradlew assembleDebug        # macOS / Linux
.\gradlew.bat assembleDebug    # Windows
```

### First run

1. Grant location (**Allow all the time** — required for background detection), Bluetooth, and notification permissions.
2. Add a vehicle and select its paired Bluetooth device.
3. Optionally exempt the app from battery optimisation so detection survives Doze.
4. To enable auto-send: **Settings → WhatsApp automation**, pick a target per vehicle, then enable the accessibility service when prompted. Use **בדיקה** next to a vehicle to verify the send works before relying on it.
5. Optionally restrict where it fires: **Settings → Automation zones**, mark the addresses that should trigger a message and set a radius for each.

## Permissions and why each is needed

| Permission | Reason |
|---|---|
| `ACCESS_FINE_LOCATION` | Capture the parking coordinates. |
| `ACCESS_BACKGROUND_LOCATION` | Detection happens with the app closed and the phone in your pocket. |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` | Read paired devices and observe connection state. `neverForLocation` is declared on scan. |
| `FOREGROUND_SERVICE_LOCATION` / `_CONNECTED_DEVICE` | Required service types for the detection service. |
| `POST_NOTIFICATIONS` | Deliver the "parking saved" notification and its share action. |
| `RECEIVE_BOOT_COMPLETED` | Recreate notification channels after a reboot. |
| `INTERNET` | Reverse geocoding only. |
| `WAKE_LOCK` | Complete an automated send that fires while the screen is off. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional, user-initiated; prevents Doze from delaying detection. |

## Privacy

Parking history lives in a local Room database on the device. There is no analytics. For a
private car the only outbound network call is reverse geocoding to Google, which receives a
coordinate pair and returns an address. Location is shared exactly once per parking event, to
the recipient you configured yourself. Records older than 30 days are deleted automatically.

A car you choose to share is the exception. Its parkings (coordinates, address, time, and the
name of whoever parked) are stored in Firestore, where only its members can read them. That
requires a Google account, and the cloud copy follows the same 30-day limit. Stop sharing, and
the last member out deletes the car's cloud data.

## Roadmap

- [x] Unit tests for `WhatsAppLinkBuilder` normalisation (`./gradlew testDebugUnitTest`)
- [ ] Unit tests for the detection pipeline
- [ ] English localisation alongside the existing Hebrew strings
- [ ] Parking-duration tracking and meter-expiry reminders
- [ ] Photo attachment for the parking spot (level, bay number)
- [ ] Wear OS companion tile
- [ ] iOS app for shared vehicles (view + manual parking; iOS can't detect a car's Bluetooth disconnect in the background)
- [ ] Push notifications for shared parkings while the app is closed (Cloud Function → FCM)

## License

MIT — see [LICENSE](LICENSE).
