# feature/map

The map tab. This is the most stateful screen in the app.

| File | Responsibility |
| --- | --- |
| `MapScreen.kt` | Orchestrator: MapLibre instance, route state, hazard layers, camera, screen state. |
| `MapSearch.kt` | Search bar, live results card, quick chips. |
| `RoutePreview.kt` | Route summary card: time, distance, hazard warnings, Drive/Bike/Walk, Via stops with edit/reorder, and category shortcuts (Fuel/Food/Rest/Health/Grocery). |
| `StopPicker.kt` | Bottom sheet that lists actual shops along the route, sorted by detour, with photos, category filters, 24/7 badges and a search fallback. |
| `NavigationModeUI.kt` | Turn-by-turn banner, structured maneuver icon, approach distance, turn list, speed bubble + speed-limit sign, voice guidance, traffic chip, navigation controls. |
| `MapLayersSheet.kt` | Legacy layer/style sheet; map style now lives in Settings, kept for a future map menu. |
| `MapGeometry.kt` | Route GeoJSON source updates, vanishing-trail trim, and distance-to-next-turn helpers. |
| `MapStyles.kt` | MapTiler style URLs, `MapStyleResolver` (falls back to an installed offline layer), and `LocationAwareMap` (the AndroidView that owns the `MapView` lifecycle, travel-mode puck). |
| `Puck3D.kt` | 3D navigation puck: top-down sedan/SUV/pickup, bike and walker sprites, plus the arrow choice, rotated as a SymbolLayer. |
| `VehicleIcon.kt` | Vehicle-icon choices and their drawable resources. |
| `VehicleIconPicker.kt` | Shared "Choose vehicle icon" sheet content, used by Settings and the map's car button. |
| `ParentMap.kt` | Simpler embedded map used by other screens. |
| `MapFocus.kt` | One-shot hand-off: "show this place". Consumed by `MapScreen` on entry. |
| `MapShops.kt` | Map amenity layer: vector category badges rasterised into MapLibre symbol images, clustered at low zoom, category chips with counts, OSM attribution. |
| `MapShopsViewModel.kt` | Cache-first shops around the user **and along a planned route**; lazy photo lookup for the tapped pin. |
| `MapViewModel.kt` | Persisted map style index and simple map UI flags. |

**Key behaviour**
- Shops on the map: `MapShopsViewModel` reuses the Nearby repository (same Room cache), then
  `MapShopMarkers` draws **category badges** (the Compose icon rasterised into a white MapLibre
  symbol). Nothing is drawn until the user picks a category in `ShopFilterRow`; selecting a chip
  also fits the camera to those shops, and the X beside "Places" hides them. A tap queries the
  rendered `shops-symbol` layer and opens the shared `ShopDetailSheet`; "Directions" plans a
  hazard-aware route.
- Multi-stop trips: `routeStops` are intermediate `GeoSearchResult`s. `RouteRepository.planWithStops`
  plans start -> stop -> ... -> destination leg by leg, merges the geometry/instructions/steps, and
  keeps hazard avoidance on every leg. The preview card has a Via strip, an **Add stop** picker, an
  **Edit** sheet for reordering/removing stops, and **Fuel / Food / Rest / Health / Grocery**
  shortcuts. The picker lists real places along the route with photos, detour distance and 24/7
  status, so the driver chooses the exact fuel station or rest stop instead of taking the nearest
  cached one. Search remains available from the picker for arbitrary places. Shops are loaded
  around the start, midpoint and destination so "along the way" works for long trips. A passed
  stop is removed from the trip; a reroute keeps only the stops still ahead.
- Navigation polish: `NavigationStep.maneuver` comes from the router (GraphHopper sign / ORS type),
  so U-turns, roundabouts, merges, ramps, sharp/slight turns and forks get the right icon. The banner
  shows the distance to the next maneuver and enlarges the icon below 180 m. The list button opens
  the full upcoming-steps sheet. The speed bubble can be tapped to switch km/h ↔ mph (persisted);
  the speed-limit sign reads OSM `maxspeed` from the bundled graph. Voice guidance speaks instruction
  changes, reroutes, wrong-way and stop-reached notices (mute button in the banner). The location
  puck is a 3D low-poly car (sedan/SUV/pickup), arrow, or bike/walk badge (`Puck3D`), rotated by the
  live bearing. Settings → Appearance → **Vehicle icon** opens the same chooser sheet as Google Maps;
  the bottom-left car button opens it directly from the map. The map keeps only the car button, a
  north/reset compass, the recenter control and SOS; zoom is pinch-only and the native compass and
  layer FAB were removed to keep the screen calm.
- Traffic/ETA: when `TOMTOM_API_KEY` is present, `TomTomTrafficRepository` samples TomTom Traffic
  Flow at the current position every 45 s. The ETA uses traffic speed, then GPS speed, then the route
  average, smoothed so it does not jump. The banner shows a Clear/Slow/Heavy/Road-closed chip. With
  no key the app simply falls back to GPS/route speed.
- Clustering: the amenity source clusters within 50 px below zoom 14; the cluster circle shows the
  count and a tap zooms in two levels.
- Lane guidance / photographic junction view is **data-blocked**: GraphHopper 1.0 does not carry
  `turn:lanes` into instructions. The approach card and fork icons are the honest substitute until
  the graph/engine carries lane data.
- Auto map style: preference `-1` means match the app theme (Dark style in dark UI, Streets in
  light). `autoStyleIndex()` resolves it; Settings still lets people pin an explicit style.
- Offline style fallback: when offline, `MapStyleResolver.resolve` picks a downloaded layer instead
  of returning a style the device cannot load.
- Hazards: `LaunchedEffect(hazards, ...)` draws blast-radius polygons from `hazard-source`. The
  radius is already in metres.
- Live rerouting: `OffRouteMonitor` judges the **raw** fix against the active route (three fixes
  beyond 45 m, or one beyond 110 m). It also catches a wrong-way driver who is still exactly on the
  line: four fixes at more than 120° against the route segment's bearing, above 3 m/s. `MapScreen`
  then replans from the raw position, replaces the route and instructions, and shows a notice in
  `NavigationModeUI`; wrong-way detections also show a red **Wrong way — turn around** banner.
  Hazard reroutes share the `isRerouting` flag so only one plan runs at a time.
  `CameraTransitionManager.snapToRoute` reports the raw offset even when it is too far away to snap.
- Simulation test: in Test Mode the SIM panel has **Deviate** (jump 200 m sideways) and **Wrong way**
  (keep the position, reverse the heading) buttons. Both feed the same `OffRouteMonitor`; the normal
  reroute path restarts the simulator on the new route, so detection, planning and replacement can
  be checked without driving.
- A shop/alert can push a pin: set `MapFocus.request(MapTarget(lat, lon, label))`, then navigate to
  `Screen.Map.route`. `MapScreen` consumes it, draws `focus-source` and animates the camera.
- `DisposableEffect` removes the route layers when the screen leaves composition.

**Do not** upgrade MapLibre casually: offline regions were downloaded with 11.8.0 and the
`OfflineManager` metadata is version-sensitive.