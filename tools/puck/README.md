# tools/puck

Generates the 3D navigation puck PNGs (`ic_puck_car_3d`, `ic_puck_bike_3d`, `ic_puck_walk_3d`)
used by `feature/map/Puck3D.kt`.

The art is an extruded honey arrow with a small white mode badge: car, bike or walk. It is drawn
programmatically so it can be regenerated and recoloured without a design tool.

```powershell
python tools/puck/generate_pucks.py
```

The script writes directly into `app/src/main/res/drawable-nodpi/`. Re-run it after changing the
palette or the badge glyphs, then rebuild the app.

The artwork is original to this repository; no third-party icon licence applies.
