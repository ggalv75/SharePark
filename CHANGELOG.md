# Changelog

Versions are numbered **V1.X** (see *Versioning* in the README).

## V1.1 — 2026-10-03

### Added
- **Swipe the map to full screen.** On the map tab, drag the parking-details sheet down to hide
  it and get the whole screen for the map. A slim bar with the car's name and address stays at the
  bottom; swipe it up (or tap it) to bring the details back. The full-screen button, tapping the
  map, and the back button still work as before.
- **See who a car is shared with.** On the vehicles screen, the members icon of a shared car now
  opens a list of everyone the car is shared with, updated live: name, "(אני)" on your own row,
  and a "יוצר השיתוף" badge on whoever created the share.
- **Reservation alerts.** When another member books a shared car, every other member gets a
  notification (one per booking, tapping it opens the car's reservations) and, if the app is
  open, a banner at the top of the screen. Bookings made while the app was closed are announced
  too: on the next launch, and by a background check every ~15 minutes.
- **Shared cars come back after reinstalling.** Signing in on a fresh install (or a new phone)
  restores every car the account is a member of, with its parking history and reservations.
  Restored cars are view-only until you tap the Bluetooth icon on the car and pick its device.
  Reservations already in the calendar are reused instead of added twice.
- **Pick the calendar for reservations.** The "הוספה ליומן" card shows which calendar bookings go
  to, offers "החלף יומן" when there is more than one, and warns when the chosen calendar is
  phone-only and doesn't sync to Google Calendar.

### Changed
- Each member's display name is stored on the shared car in Firestore (`memberNames`), written
  when the car is shared or joined, refreshed when the app starts, and removed when a member stops
  sharing. Members who joined on V1.0 appear unnamed until they open V1.1.
- `firestore.rules`: a member may set or remove their own entry in `memberNames` (and no one
  else's). **Deploy the rules** for names to sync:
  `firebase deploy --only firestore:rules --project <your-project-id>`. Until then, sharing and
  joining keep working; only the names are missing.
- Reservations now go to a Google calendar by default. Many phones flag their local "My
  calendar" as primary too, and it was picked first, so bookings never reached Google Calendar.
  Upcoming bookings already in the wrong calendar are moved on the next sync.
- APKs are named after the version: `SharePark-V1.1-debug.apk`.

## V1.0

First release: Bluetooth parking detection, map, history, WhatsApp automation and zones, trusted
contacts, shared vehicles, and reservations synced to members' calendars.
