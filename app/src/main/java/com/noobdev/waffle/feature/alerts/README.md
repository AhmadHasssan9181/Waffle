# feature/alerts

Offline-first emergency feed.

| File | Responsibility |
| --- | --- |
| `AlertsScreen.kt` | Feed, filter chips, sort, detail sheet, AI brief. |
| `AlertsViewModel.kt` | Room flow, filtering, sorting, refresh, sync trigger. |
| `data/EmergencyFeedRepository.kt` | Pulls USGS `2.5_day` GeoJSON + GDACS RSS, normalises into `AlertEntity`, upserts. |
| `data/EmergencySyncWorker.kt` | WorkManager job (periodic, 30 min) and one-shot sync entry. |
| `data/EmergencySyncScheduler.kt` | Schedules the worker from app start / pull-to-refresh. |

**Notification rule:** only `WARNING`/`CRITICAL` alerts inside the hazard radius + 150 km post a
notification. `INFO`/`SAFE` are feed-only.

**Freshness:** the list is always Room. A cold start with no signal shows whatever the last sync
saw. Pull-to-refresh calls `syncNow`; it does not clear the list while waiting.