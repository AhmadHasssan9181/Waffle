# tools/maps

The Pakistan map pack is too large for the APK (~700 MB of tiles and a place index), so it is
side-loaded into the app's own storage. Waffle reads it from there and silently falls back to the
online basemap when a piece is missing.

## What the app expects

Everything lives under `Android/data/com.noobdev.waffle/files/` (the app also checks its internal
`files/` directory first, which is where `run-as` copies land):

| File | Used by |
|---|---|
| `pakistan_vector.pmtiles` | OpenMapTiles basemap, `MapStyleResolver.localVectorStyle` |
| `pakistan_places.pmtiles` | Overture POI layer, clickable places on the map |
| `places_search.db` | `PlacesSearchDb` — FTS5 index of ~850k places |
| `fonts/<stack>/<range>.pbf` | Label glyphs referenced by the style JSON |
| `style/sprite.json` + `sprite.png` | POI icons |

The style itself ships in the APK as `assets/vector_styles/style_{light,dark,field}_both.json`.
`localVectorStyle` rewrites the `pmtiles://`, `assets/fonts/...` and `assets/sprite/...` URLs to
point at the installed files, so the same style works from any install location.

Settings → Offline → **Pakistan map pack** reports exactly which of the five pieces were found.

## Installing

```powershell
python tools/maps/install_offline_pack.py
```

Defaults to `build/release-preview28`, which is where the release bundle stages the assets:

```
build/release-preview28/
  pakistan_vector.pmtiles
  pakistan_places.pmtiles
  places_search.db
  fonts.zip
  sprite.zip
  MANIFEST.json
```

Useful flags:

- `--pack <dir>` — read the assets from somewhere else.
- `--serial <serial>` — pick a device when more than one is attached.
- `--verify` — compare sha256 against `MANIFEST.json` before pushing. Note that
  `places_search.db` is often regenerated after the manifest is written, so a mismatch there is
  expected unless the manifest was refreshed too.
- `--dry-run` — print the adb commands without running them.

The script extracts `fonts.zip` and `sprite.zip` on the host and pushes the resulting trees, so the
phone never needs an unzip binary.

## Regenerating the manifest

The pack is rebuilt often (tiles, place index, fonts), and a stale `MANIFEST.json` makes
`--verify` fail for the wrong reason. After any rebuild:

```powershell
python tools/maps/build_manifest.py
```

It rewrites `MANIFEST.json` and `SHA256SUMS.txt` for every release artefact in the pack directory,
in a stable order, and prints the size and hash of each file.

To confirm the phone matches the pack, hash it on the device — Android's toybox `sha256sum` is
fast enough for the 200 MB index:

```bash
adb shell sha256sum /sdcard/Android/data/com.noobdev.waffle/files/places_search.db
```

## Manual install

```bash
adb push pakistan_vector.pmtiles  /sdcard/Android/data/com.noobdev.waffle/files/
adb push pakistan_places.pmtiles  /sdcard/Android/data/com.noobdev.waffle/files/
adb push places_search.db         /sdcard/Android/data/com.noobdev.waffle/files/
# extract fonts.zip -> files/fonts/...   and sprite.zip -> files/style/
```

Then force-stop Waffle and reopen it.

## Regenerating the pack

The tile archives come from `build/offline_pack/` (see `tools/places` and `tools/roads` for the
source pipelines). The place index is built from the Overture extract with the same
`cat`/`bcat`/`addr`/`phone`/`website` columns that `PlacesSearchDb` queries.
