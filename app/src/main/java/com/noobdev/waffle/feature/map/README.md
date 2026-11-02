# feature/map

The map tab. This is the most stateful screen in the app.

| File | Responsibility |
| --- | --- |
| `MapScreen.kt` | Orchestrator: MapLibre instance, route state, hazard layers, camera, screen state. |
| `MapSearch.kt` | Search bar, live results card, quick chips. |
| `RoutePreview.kt` | Route summary card: time, distance, hazard warnings, start/alternate/end actions. |
| `NavigationModeUI.kt` | Turn-by-turn banner, next-instruction icon, navigation controls. |
| `MapLayersSheet.kt` | Bottom sheet with layer/style cards and the style picker. |
| `MapGeometry.kt` | Route GeoJSON source updates and the vanishing-trail trim helper. |
| `MapStyles.kt` | MapTiler style URLs, `MapStyleResolver` (falls back to an installed offline layer), and `LocationAwareMap` (the AndroidView that owns the `MapView` lifecycle). |
| `ParentMap.kt` | Simpler embedded map used by other screens. |
| `MapFocus.kt` | One-shot hand-off: "show this place". Consumed by `MapScreen` on entry. |
| `MapShops.kt` | Map shop layer: GeoJSON, category-coloured dots, filter chips, OSM attribution. |
| `MapShopsViewModel.kt` | Cache-first shops around the user; lazy photo lookup for the tapped pin. |
| `MapViewModel.kt` | Persisted map style index and simple map UI flags. |

**Key behaviour**
- Shops on the map: `MapShopsViewModel` reuses the Nearby repository (same Room cache), then
  `MapShopMarkers` draws category dots. A tap queries the rendered `shops-dot` layer and opens the
  shared `ShopDetailSheet`; "Directions" plans a hazard-aware route. Dots are deliberately unlabelled
  so city zoom does not become a wall of text.
- Auto map style: preference `-1` means match the app theme (Dark style in dark UI, Streets in
  light). `autoStyleIndex()` resolves it; Settings still lets people pin an explicit style.
- Offline style fallback: when offline, `MapStyleResolver.resolve` picks a downloaded layer instead
  of returning a style the device cannot load.
- Hazards: `LaunchedEffect(hazards, ...)` draws blast-radius polygons from `hazard-source`. The
  radius is already in metres.
- Live rerouting: `OffRouteMonitor` judges the **raw** fix against the active route (three fixes
  beyond 45 m, or one beyond 110 m). `MapScreen` then replans from the raw position, replaces the
  route and instructions, and shows a notice in `NavigationModeUI`. Hazard reroutes share the
  `isRerouting` flag so only one plan runs at a time. `CameraTransitionManager.snapToRoute` reports
  the raw offset even when it is too far away to snap.
- A shop/alert can push a pin: set `MapFocus.request(MapTarget(lat, lon, label))`, then navigate to
  `Screen.Map.route`. `MapScreen` consumes it, draws `focus-source` and animates the camera.
- `DisposableEffect` removes the route layers when the screen leaves composition.

**Do not** upgrade MapLibre casually: offline regions were downloaded with 11.8.0 and the
`OfflineManager` metadata is version-sensitive.