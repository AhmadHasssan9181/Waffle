# Offline road search index

`offline_places.json` contains POIs only, so searching for a road such as “Mall Road Rawalpindi”
used to fail. This tool builds `app/src/main/assets/offline_roads.json` from named OSM highways.
The app searches the road name/ref and routes to the way's bounding-box centre, which the router
snaps to the nearest road.

## Build

```powershell
# Named highways in the Rawalpindi / south Islamabad quadrant.
$query = '[out:json][timeout:120];way["highway"]["name"](33.45,72.85,33.70,73.10);out tags center;'
[System.IO.File]::WriteAllText("$pwd\roads_q1.overpass", $query, (New-Object System.Text.UTF8Encoding($false)))
curl.exe --data-urlencode "data@roads_q1.overpass" https://overpass-api.de/api/interpreter -o roads_q1.json

# Add the other quadrants you have (q2e/q3/q4) as extra arguments.
python tools/roads/build_road_data.py roads_q1.json roads_q3.json roads_q4.json `
  app/src/main/assets/offline_roads.json
```

The current sidecar covers the Rawalpindi/south Islamabad and north-west Islamabad quadrants
(q1/q3/q4). The east quadrant (q2e) can be added later; the public Overpass endpoint returns 502
when busy.

The script deduplicates by name and roughly 10 km of centre distance, so a long road does not fill
the search list with duplicate segments.

Data © OpenStreetMap contributors, ODbL.
