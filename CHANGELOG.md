# Changelog

Versions are numbered **V1.X** (see *Versioning* in the README).

## V1.2 — 2026-10-03

### Added
- **Swipe between cars on the map.** The parking sheet pages through all your cars: swiping makes
  that car active and the map glides to it. Every parked car has a pin (tap it to jump to that
  car), and the full-screen bar can be swiped too.
- **See who parked, in history.** On shared cars each history entry shows who parked it ("אני"
  for your own).
- **Reservation alerts.** When another member books a shared car, every other member gets a
  notification (one per booking, tapping it opens the car's reservations) and, if the app is
  open, a banner at the top of the screen. Bookings made while the app was closed are announced
  too: on the next launch, and by a background check every ~15 minutes.
- **Pick the calendar for reservations.** The "הוספה ליומן" card shows which calendar bookings go
  to, offers "החלף יומן" when there is more than one, and warns when the chosen calendar is
  phone-only and doesn't sync to Google Calendar.
- **Shared cars come back after reinstalling.** Signing in on a fresh install (or a new phone)
  restores every car the account is a member of, with its parking history and reservations.
  Restored cars are view-only until you tap the Bluetooth icon on the car and pick its device.
  Reservations already in the calendar are reused instead of added twice.
- **Pair Bluetooth to a shared car.** A view-only shared car shows a Bluetooth icon on the
  vehicles screen; pick the car's device and this phone detects its parkings too.

### Changed
- Reservations now go to a Google calendar by default. Many phones flag their local "My
  calendar" as primary too, and it was picked first, so bookings never reached Google Calendar.
  Upcoming bookings already in the wrong calendar are moved on the next sync.
- A shared car's whole 30-day parking history is synced, not only the latest 50 parkings.
- Release builds are signed with a key kept off the repo (`keystore.properties`, see the README).
  **Switching from a debug build to the signed release needs a one-time uninstall**; shared cars,
  their history and reservations come back after signing in.

### Fixed
- Background jobs (the daily cleanup and the reservation check) never ran: WorkManager started
  with its default configuration before the app could hand it the Hilt worker factory.

## V1.1 — 2026-10-03

### Added
- **Swipe the map to full screen.** On the map tab, drag the parking-details sheet down to hide
  it and get the whole screen for the map. A slim bar with the car's name and address stays at the
  bottom; swipe it up (or tap it) to bring the details back. The full-screen button, tapping the
  map, and the back button still work as before.
- **See who a car is shared with.** On the vehicles screen, the members icon of a shared car now
  opens a list of everyone the car is shared with, updated live: name, "(אני)" on your own row,
  and a "יוצר השיתוף" badge on whoever created the share.

### Changed
- Each member's display name is stored on the shared car in Firestore (`memberNames`), written
  when the car is shared or joined, refreshed when the app starts, and removed when a member stops
  sharing. Members who joined on V1.0 appear unnamed until they open V1.1.
- `firestore.rules`: a member may set or remove their own entry in `memberNames` (and no one
  else's). **Deploy the rules** for names to sync:
  `firebase deploy --only firestore:rules --project <your-project-id>`. Until then, sharing and
  joining keep working; only the names are missing.
- APKs are named after the version: `SharePark-V1.1-debug.apk`.

## V1.0

First release: Bluetooth parking detection, map, history, WhatsApp automation and zones, trusted
contacts, shared vehicles, and reservations synced to members' calendars.
