# core/navigation

All the maths behind "is this route safe?" — deliberately free of Android UI code so it can be
unit-tested on the JVM.

| File | Responsibility |
| --- | --- |
| `GeoGeometry.kt` | Distance, bearing, point-in-polygon, circle polygons, route-length helpers. |
| `BlastRadius.kt` | Converts an alert into a danger radius. Earthquakes use `10^(0.43M - 0.8)` km; floods use the explicit radius, otherwise severity defaults. Routing radius = 65% of the display radius. |
| `HazardRegistry.kt` | Normalises live alerts into `HazardZone`s (id, centre, radius, severity) for the map and router. |
| `HazardDetourPlanner.kt` | Builds via-points so a route goes around a hazard, and picks the shorter valid detour. |
| `NavigationManager.kt` | Current instruction, distance to next turn, off-route detection, arrival. |
| `RouteSimulator.kt` | Drives the demo/navigation animation along a route. |
| `CameraTransitionManager.kt` | Smooth camera interpolation between map states. |

**Invariants**
- Hazard radius is metres. Never pass kilometres into the map layers.
- `GeoGeometry` works in degrees, latitude first, matching `GeoPoint(lat, lon)` everywhere.
- Tests: `app/src/test/java/com/noobdev/waffle/core/navigation/GeoGeometryTest.kt`.