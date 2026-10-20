# core/storage

| File | Responsibility |
| --- | --- |
| `AssetReader.kt` | `loadJsonFromAsset(context, name)` reads `assets/disaster_data/<name>`. |
| `AssetCopier.kt` | Copies a large asset (the routing graph) out of the APK exactly once. |
| `StorageUtils.kt` | Free-space queries and human-readable byte formatting. |

Offline map downloads check `StorageUtils.hasRoomFor` before starting, with a safety margin —
running out of disk mid-download corrupts the tile pack.