# Lane sidecar builder

The bundled GraphHopper 1.0 graph does not carry lane tags, and the Islamabad–Rawalpindi OSM
extract currently has **no `turn:lanes` tags**. It does have `lanes` tags, so Waffle ships a small
sidecar with the way geometry and lane counts. `LaneGuidanceRepository` uses it to show a lane-count
hint for the next maneuver.

## Regenerate

```powershell
# Rawalpindi / south Islamabad quadrant used by the current sidecar.
$query = '[out:json][timeout:120];way["highway"]["lanes"](33.45,72.85,33.70,73.10);out tags geom;'
[System.IO.File]::WriteAllText("$pwd\lanes.overpass", $query, (New-Object System.Text.UTF8Encoding($false)))
curl.exe --data-urlencode "data@lanes.overpass" https://overpass-api.de/api/interpreter -o lanes.json

python tools/lanes/build_lane_data.py lanes.json app/src/main/assets/lane-data-islamabad.json
```

The public Overpass instance returns 502/504 when busy; retry later or split the bounding box.

## What the app does with it

- The sidecar stores `lanes`, `lanes:forward`, `lanes:backward`, `oneway` and the way geometry.
- For the next instruction, the repository finds the nearest way within 35 m, checks whether the
  route is travelling with or against the way, and derives a lane count.
- The UI highlights the leftmost lane for a left turn, the rightmost for a right turn, and all lanes
  for straight/roundabout. This is a **hint**, not a `turn:lanes` assignment.
- If the maneuver is outside the sidecar coverage, no lane row is shown rather than a fake one.

Data © OpenStreetMap contributors, ODbL.
