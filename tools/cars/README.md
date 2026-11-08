# 3D vehicle models

Waffle's navigation puck renders real 3D models. Every model in `app/src/main/assets/cars/` is an
original Waffle asset, generated procedurally — there is no third-party model in the APK.

Packaged files:

- `sedan-sports.glb` — fastback sports sedan
- `suv-luxury.glb` — luxury SUV with roof rails
- `truck-flat.glb` — crew-cab pickup with a cargo bed
- `motorcycle.glb` — sportbike with a 3D rider
- `bicycle.glb` — bicycle with a 3D cyclist
- `walker.glb` — pedestrian in the frame-0 pose
- `colormap.png` — shared 512×512 material palette atlas
- `LICENSE.txt` — asset notice

## Regenerating

```powershell
python tools/cars/generate_models.py
```

That one script writes everything downstream:

- the six `.glb` models and `colormap.png` into `app/src/main/assets/cars/`
- the 12-frame walk cycle into `app/src/main/assets/walker/frame_00..11.png`
- the navigation pucks (`ic_puck_{sedan,suv,pickup,bike,walk,arrow,dot}*`) into
  `app/src/main/res/drawable-nodpi/`

`tools/puck/generate_pucks.py` and `tools/pucks/render_pucks.py` are thin wrappers that call the
same script, so there is a single source of truth for the artwork.

## Conventions

Every model is authored so the renderer can treat them identically:

- forward is **+Z**, up is **+Y**, origin sits on the ground plane between the wheels
- the ground footprint is centred on the origin, so the puck stays on the route line
- materials come from the shared palette atlas via `TEXCOORD_0`, so one texture covers every part
- cars are normalised by length, the pedestrian by height

`GltfCarRenderer` parses the GLB meshes, node transforms, normals, UVs and the palette, then
rasterises the model with a software z-buffer at the requested bearing, with supersampling, soft
ground shadow and projected headlight beams.

## Licence

The models are original artwork made for this repository and are covered by the Waffle Personal Use
License. Do not swap in a model under a more restrictive licence without updating
`app/src/main/assets/cars/LICENSE.txt` and `ATTRIBUTIONS.md`.
