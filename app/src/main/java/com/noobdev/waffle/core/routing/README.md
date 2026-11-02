# core/routing

Route planning with a strict order: **offline first, online second**.

| File | Responsibility |
| --- | --- |
| `RouteRepository.kt` | Public entry point. Tries the bundled GraphHopper graph, falls back to OpenRouteService, then applies hazard avoidance. `planWithStops` plans each leg and `mergeRoutePlans` stitches them. |
| `OfflineRoutingManager.kt` | Copies the graph from assets to internal storage once, then opens it. |
| `LocalRoutingEngine.kt` | GraphHopper wrapper. Profiles must match the graph exactly. |
| `GraphHopperRouteMapper.kt` | Converts a GraphHopper `Path` into the app's `RouteResult` + `NavigationStep`s. |

**GraphHopper is pinned to 1.0.** It is the last release with offline support and the bundled graph
in `app/src/main/assets/graph-cache/` was built with it. Do not bump the version without rebuilding
the graph with `tools/graph-builder`.

**Profile contract (builder and engine must agree):**

| App profile | GraphHopper profile | Weighting | CH |
| --- | --- | --- | --- |
| `car` | `car` | `fastest` | yes |
| `foot` | `foot` | `shortest` | no |
| `bike` | `bike` | `fastest` | no |

Elevation is off. `car` is the only profile with contraction hierarchies.

**Multi-stop invariant:** `planWithStops` plans each leg separately, so every leg gets the same
hazard avoidance. The shared boundary coordinate is removed from the next leg, and each leg's
`NavigationStep.wayPointIndex` is offset into the merged coordinate list. Do not concatenate raw
legs without that offset: turn-by-turn would point at the wrong geometry.

**Coverage:** only the Islamabad–Rawalpindi extract is bundled. Other catalog areas have maps but
no offline routing; the repository falls back to ORS there (which needs a connection).