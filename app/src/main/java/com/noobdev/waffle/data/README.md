# data/

Shared data plumbing, split by where the data lives.

| Folder | Contents |
| --- | --- |
| `model/` | Plain Kotlin models shared by several features (`DisasterAlert`, `StatusLevel`). |
| `local/` | Room: the `waffle_database` alert cache and the Retrofit client for legacy endpoints. |
| `remote/` | Network clients: OpenCage, Open-Meteo, OpenRouteService, Gemini, offline geocoder, MapTiler style URLs. |

Feature-specific data (shops, flood intel) lives inside its feature package instead:
`feature/shops/data`, `feature/flood/data`.