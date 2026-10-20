# feature/

One folder per user-facing capability. A feature owns its screen, its ViewModel and (if it needs
one) its own data layer.

| Feature | Tab? | What it does |
| --- | --- | --- |
| `home/` | yes | Safety status, weather, shortcuts, nearest alerts, emergency numbers. |
| `map/` | yes | MapLibre map, search, routing, hazard circles, turn-by-turn. |
| `shops/` | yes | Nearby shops/services from OSM, cached in their own Room DB, with Commons photos. |
| `alerts/` | yes | Offline-first alert feed from Firebase + USGS + GDACS. |
| `rescue/` | yes | Honest SOS: dial services or send coordinates by SMS. |
| `offline/` | pushed | Download multi-layer offline map packs. |
| `settings/` | pushed | Theme, map style, notifications, emergency numbers, cache. |
| `flood/` | layers | Flood sector data and heat/overlay drawing for the map. |
| `hazards/` | none yet | Early hazard-card sketches; **not wired into navigation**. |

**Rules**
- A feature may depend on `core/`, `data/` and `design/`, never on another feature's screen.
  Cross-feature hand-off goes through a small object (example: `feature/map/MapFocus.kt`).
- Keep `ViewModel` next to its screen. No screen builds a Retrofit client directly.
- Sub-README in each folder explains the files and any invariant that is easy to break.