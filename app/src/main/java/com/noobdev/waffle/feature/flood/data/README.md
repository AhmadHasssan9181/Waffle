# feature/flood/data

Bundled flood intelligence. Read-only at runtime.

- `FloodData.kt` — provinces, sectors and historical records used to shade the map.
- `FloodIntelModels.kt` — `GeminiRequest` / `GeminiResponse` / `Content` / `Part` and the flood UI
  model. The Gemini request models are consumed by `data/remote/GeminiClient`.
- `RecentFloodData.kt` — small fixtures for recent events.

Keep this package free of Android imports so it stays unit-testable.