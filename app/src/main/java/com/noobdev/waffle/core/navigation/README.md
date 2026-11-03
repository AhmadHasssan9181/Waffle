# core/navigation

All the maths behind "is this route safe?" plus the long-lived navigation session. The routing
maths is deliberately free of Android UI code so it can be unit-tested on the JVM. The service,
controller and store are the pieces that keep a trip alive when the map screen is not composed.

| File | Responsibility |
| --- | --- |
| `GeoGeometry.kt` | Distance, bearing, point-in-polygon, circle polygons, route-length helpers. |
| `BlastRadius.kt` | Converts an alert into a danger radius. Earthquakes use `10^(0.43M - 0.8)` km; floods use the explicit radius, otherwise severity defaults. Routing radius = 65% of the display radius. |
| `HazardRegistry.kt` | Normalises live alerts into `HazardZone`s (id, centre, radius, severity) for the map and router. |
| `HazardDetourPlanner.kt` | Builds via-points so a route goes around a hazard, and picks the shorter valid detour. |
| `OffRouteMonitor.kt` | Decides when live GPS is genuinely off the line (accuracy gate, consecutive fixes, retry cooldown). |
| `NavigationManager.kt` | Session facade for the map screen: route state, road closures, hazard proximity. |
| `NavigationSession.kt` | The single source of truth for the live trip. `StateFlow<NavigationSnapshot>` holds route, destination, stops, fix, instruction index, ETA, speed limit, reroute/wrong-way flags. |
| `NavigationController.kt` | The live navigation loop: fused location, snap-to-route, instruction progress, ETA, speed limit, off-route/wrong-way reroute and hazard reroute. Lives inside the service, not the composable. |
| `NavigationService.kt` | Foreground service with a `location` type, partial wake lock and ongoing turn-by-turn notification. Owns the controller and survives tab changes, backgrounding and PiP. |
| `NavigationStore.kt` | Persists route, destination, stops and mute state to `navigation_session.json` so a process restart can resume the trip. |
| `PipState.kt` | Whether the activity is in picture-in-picture; the map hides its chrome and the bottom bar. |
| `RouteSimulator.kt` | Drives the demo/navigation animation along a route. |
| `CameraTransitionManager.kt` | Smooth camera interpolation between map states. |

**How the pieces fit**
- `MapScreen` starts a trip with `NavigationSession.start(...)`. That sets the snapshot and starts
  `NavigationService`.
- The service creates `NavigationController`, which owns GPS and publishes every fix into
  `NavigationSession`.
- `MapScreen` only observes the snapshot and mirrors it into local UI state. Switching tabs,
  entering PiP, or reopening the app therefore resumes the same trip.
- The ongoing notification is rebuilt from the snapshot on each fix. Tapping it opens the Map tab;
  its Exit action stops the service and clears the store.
- `NavigationStore` is the safety net for process death. `MainActivity` restores it only when the
  singleton is not already active, so a live trip is never reset to its last saved route.

**Invariants**
- Hazard radius is metres. Never pass kilometres into the map layers.
- `GeoGeometry` works in degrees, latitude first, matching `GeoPoint(lat, lon)` everywhere.
- The service must own the GPS callback. Do not register a second live callback in `MapScreen` for
  a real trip; it will duplicate progress and fight the controller.
- The controller must set `stopped` before cancelling its scope so a queued fix cannot publish an
  active snapshot after the user exits.
- Tests: `app/src/test/java/com/noobdev/waffle/core/navigation/GeoGeometryTest.kt`.
