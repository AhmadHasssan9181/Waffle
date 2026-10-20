# data/remote

One file per external service. All calls are `suspend` and return nullable/`Result` values — no
screen should see an exception.

| File | Service | Key? |
| --- | --- | --- |
| `OpenCage.kt` | Reverse geocoding (place names) | `OPENCAGE_API_KEY` in `local.properties` |
| `OfflineGeocoder.kt` | Bundled fallback geocoder, no network | none |
| `OpenMeteo.kt` | Hourly weather | none |
| `OpenRouteService.kt` | Online routing + `avoid_polygons` | `ORS_API_KEY` |
| `GeminiClient.kt`, `GeminiInstance.kt` | AI situation brief | `GEMINI_API_KEY` |
| `MapStyles.kt` (in `feature/map`) | MapTiler style URLs | `MAPTILER_API_KEY` |

Keys are injected through `build.gradle.kts` into `BuildConfig`. Rotate any key that has ever been
committed. API clients here must set a descriptive `User-Agent`; Overpass and Wikimedia require it.