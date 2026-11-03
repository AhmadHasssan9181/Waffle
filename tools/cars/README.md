# 3D vehicle models

Waffle's navigation puck uses the **Kenney Car Kit** (CC0 1.0), downloaded from:

https://kenney.nl/assets/car-kit

The packaged files are in `app/src/main/assets/cars/`:

- `sedan-sports.glb`
- `suv-luxury.glb`
- `truck-flat.glb`
- `colormap.png` (shared Kenney palette)
- `LICENSE.txt`

`GltfCarRenderer` parses the GLB meshes, node transforms, normals, UVs and the shared palette,
then rasterises the car with a software z-buffer at the requested bearing. The model's body/wheel
palette entries are remapped to Waffle's red/amber/blue car colours so the three vehicle choices
stay distinct.

CC0 does not require attribution, but the source is credited in the asset licence and release
notes. Do not replace these files with a model under a more restrictive licence without updating
the notices.
