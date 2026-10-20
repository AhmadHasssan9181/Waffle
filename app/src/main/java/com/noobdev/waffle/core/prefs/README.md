# core/prefs

`PreferencesManager` is the single source of truth for user settings, backed by DataStore
(`waffle_settings`).

Keys currently stored:
- `app_theme` — `SYSTEM`, `LIGHT` or `DARK`. Unknown legacy values (`OCEAN`, `SUNSET`, ...) fall
  back to `SYSTEM` instead of crashing.
- `map_style_index` — index into `mapTilerStyles`.
- `notifications_enabled`, `flood_alerts`, `earthquake_alerts`, `wildfire_alerts`.
- `user_lat`, `user_lng`, `last_location_name`.

Everything is exposed as a `Flow`; screens collect it, they never read DataStore directly.