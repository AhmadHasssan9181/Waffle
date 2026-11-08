# tools/puck

Generates the navigation puck PNGs used by `feature/map/Puck3D.kt`:

- `ic_puck_sedan_3d`, `ic_puck_suv_3d`, `ic_puck_pickup_3d` — the 3D cars rendered from the real
  `.glb` models, nose pointing up so `iconRotate` can align them with the GPS bearing.
- `ic_puck_dot` — the neutral blue location dot used outside active navigation (idle and route
  preview).
- `ic_puck_bike_3d` — the 3D bicycle with its cyclist, nose up.
- `ic_puck_walk_3d` — the 3D pedestrian, facing up.
- `ic_puck_arrow_3d` — the classic navigation arrow.

The sprites are rendered from the same meshes the map uses at runtime, so the picker and the live
puck never disagree.

```powershell
python tools/puck/generate_pucks.py
```

This is a thin wrapper around `tools/cars/generate_models.py`, which owns the meshes, the palette
and the software renderer. Run it after changing a model or the palette, then rebuild the app.

The artwork is original to this repository; no third-party icon licence applies.
