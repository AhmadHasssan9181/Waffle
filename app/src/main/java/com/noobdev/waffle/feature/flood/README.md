# feature/flood

Flood sector data and map overlays.

| File | Responsibility |
| --- | --- |
| `HeatMaps.kt` | Nine EMSR838 sector definitions, JSON loading and the legacy `HeatmapDemoScreen`. |
| `data/FloodData.kt` | Large bundled dataset (provinces, sectors, historical flood records). |
| `data/FloodIntelModels.kt` | Models used by the Gemini flood-intel path. |
| `data/RecentFloodData.kt` | Recent-event fixtures. |

`features/flood` is a layer provider for `MapScreen`, not a tab. Data is read from
`assets/disaster_data/` via `core/storage/AssetReader.loadJsonFromAsset`.

**Wiring:** Rescue ? **Flood maps** requests a `MapTarget` with a `Sector`; `MapScreen` consumes it,
loads `loadSectorData` (depth/water/extent/roads/built-up/footprint/limit), fits the camera and
shows `FloodLegend`. The legend close removes every flood layer/source. `HeatmapDemoScreen` is
legacy and not reachable; the real entry point is Rescue.

The data file is ~2.9k lines; edit it with a script, not by hand.