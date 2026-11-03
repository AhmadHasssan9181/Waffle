# tools/puck

Generates the 3D navigation puck PNGs used by `feature/map/Puck3D.kt`:

- `ic_puck_sedan_3d`, `ic_puck_suv_3d`, `ic_puck_pickup_3d` — 3/4 rear-right low-poly cars.
- `ic_puck_bike_3d`, `ic_puck_walk_3d` — 3D arrow with a bike/walk badge.
- `ic_puck_arrow_3d` — the classic navigation arrow.

The art is drawn programmatically so it can be regenerated and recoloured without a design tool.

```powershell
python tools/puck/generate_pucks.py
```

The script writes directly into `app/src/main/res/drawable-nodpi/`. Re-run it after changing the
palette or the badge glyphs, then rebuild the app.

The artwork is original to this repository; no third-party icon licence applies.
