# feature/offline

Downloadable map packs. Four layers per area, each with its own zoom cap and size estimate.

| File | Responsibility |
| --- | --- |
| `OfflineMapsScreen.kt` | Area list, per-layer download/pause/resume/delete, storage summary. |
| `OfflineMapManager.kt` | MapLibre `OfflineManager` wrapper, progress state, install/delete, startup refresh. |
| `OfflineRegionCatalog.kt` | `OfflineLayer` enum, target definitions, per-area zoom caps and MB estimates. |

| Layer | Type | Max zoom | Estimate |
| --- | --- | --- | --- |
| Streets | vector | 14 | 32 KB/tile |
| Satellite | raster | 14 | 110 KB/tile |
| Terrain | vector | 14 | 32 KB/tile |
| Dark | vector (reuses Streets) | 14 | 32 KB/tile |

Checks before download: online, unmetered for packs ≥ 25 MB (confirmation otherwise), and
`StorageUtils.hasRoomFor` with a safety margin. Deleting a pack calls `OfflineMapManager.delete`
so MapLibre's metadata stays in sync with the files on disk.