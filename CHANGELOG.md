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
