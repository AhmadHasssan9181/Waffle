# feature/home

The "am I safe right now" screen.

| File | Responsibility |
| --- | --- |
| `HomeScreen.kt` | Header, greeting, safety hero, weather tiles, action grid, alert preview, emergency numbers. |
| `HomeViewModel.kt` | Room alert flow, reverse geocoding, weather, greeting. |

Notes:
- Alerts come from **Room** (`AlertDao.getAllAlertsFlow()`), not directly from Firestore, so the
  screen still works offline and agrees with the Alerts tab.
- `EmergencySyncScheduler.syncNow()` runs on open; the periodic worker does the rest.
- Location comes from `FusedLocationProviderClient.lastLocation`. No fix means the header keeps
  showing the last cached place name.
- The old debug "Database Status" card and the placeholder OpenCage key are gone on purpose.