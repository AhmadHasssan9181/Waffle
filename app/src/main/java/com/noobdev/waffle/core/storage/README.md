# core/storage

| File | Responsibility |
| --- | --- |
| `AssetReader.kt` | `loadJsonFromAsset(context, name)` reads `assets/disaster_data/<name>`. |
| `AssetCopier.kt` | Copies the routing graph out of the APK; `.copy_complete` stores a graph version so an app update replaces a stale graph. |
| `StorageUtils.kt` | Free-space queries and human-readable byte formatting. |

Offline map downloads check `StorageUtils.hasRoomFor` before starting, with a safety margin —
running out of disk mid-download corrupts the tile pack.

The graph marker is versioned. Bump `GRAPH_VERSION` in `AssetCopier` whenever the bundled graph
changes (profiles, CH preparation, extract bounds), or existing installs keep the old graph and
new travel modes fail offline.