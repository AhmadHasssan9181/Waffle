# data/model

Models that more than one feature needs.

- `DisasterAlert.kt` — the alert shape used by Firebase, USGS/GDACS ingestion and Room. Field names
  are snake_case because they are persisted and come straight off the wire; do not rename them.
- `StatusLevel.kt` — `CRITICAL`, `WARNING`, `INFO`, `SAFE`. Lives here (not in the design system)
  so data does not depend on UI. Screens map it to colour through `statusColor()`.

`DisasterAlert.location_name` is a `var` for Firebase deserialisation; copy it to a local `val`
before a null check when you need a smart cast.