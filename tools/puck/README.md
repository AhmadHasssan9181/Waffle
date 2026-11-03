# tools/puck

Generates the navigation puck PNGs used by `feature/map/Puck3D.kt`:

- `ic_puck_sedan_3d`, `ic_puck_suv_3d`, `ic_puck_pickup_3d` - top-down low-poly cars with the nose
  pointing up, so `iconRotate` can align them with the GPS bearing without a side-on look.
- `ic_puck_dot` - the neutral blue location dot used outside active navigation (idle and route
  preview).
- `ic_puck_bike_3d` - a top-down 3D bicycle and rider, nose up.
- `ic_puck_walk_3d` - a top-down 3D walker, facing up.
- `ic_puck_arrow_3d` - the classic navigation arrow.

The art is drawn programmatically so it can be regenerated and recoloured without a design tool.

```powershell
python tools/puck/generate_pucks.py
```

The script writes directly into `app/src/main/res/drawable-nodpi/`. Re-run it after changing the
palette or the badge glyphs, then rebuild the app.

The artwork is original to this repository; no third-party icon licence applies.
