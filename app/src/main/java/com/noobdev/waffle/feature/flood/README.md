# feature/flood

Flood sector data and map overlays.

| File | Responsibility |
| --- | --- |
| `HeatMaps.kt` | Sector definitions, JSON loading, heat/overlay composables for the map. |
| `data/FloodData.kt` | Large bundled dataset (provinces, sectors, historical flood records). |
| `data/FloodIntelModels.kt` | Models used by the Gemini flood-intel path. |
| `data/RecentFloodData.kt` | Recent-event fixtures. |

`features/flood` is a layer provider for `MapScreen`, not a tab. Data is read from
`assets/disaster_data/` via `core/storage/AssetReader.loadJsonFromAsset`.

The data file is ~2.9k lines; edit it with a script, not by hand.