# core/

Cross-cutting plumbing that is not tied to a single screen.

| Folder | What lives there |
| --- | --- |
| `navigation/` | Geometry, hazard zones, blast radius, camera moves, turn-by-turn state. No UI. |
| `routing/` | Route planning: offline GraphHopper first, ORS second. No UI. |
| `storage/` | Asset copying, free-space checks, reading bundled JSON. |
| `prefs/` | DataStore-backed settings (theme, map style, last location). |
| `network/` | Connectivity + metered/unmetered checks. |
| `notifications/` | Notification channels, FCM service, alert posting. |

**Rule:** nothing in `core/` imports a screen or a Compose UI file. If a helper needs to draw
something, it belongs in `design/` or the owning `feature/` package.