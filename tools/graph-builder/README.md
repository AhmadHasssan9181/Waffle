# Offline routing graph builder

Waffle routes **on the device** with GraphHopper 1.0. The routing graph that ships inside the APK
lives in `app/src/main/assets/graph-cache/` and is copied into app storage on first launch.

This module regenerates that graph from OpenStreetMap data.

## How it works

1. **Extract OSM data** for the area you want (roads only is enough for routing).
2. **Import** it with GraphHopper 1.0, which writes the graph folder.
3. **Verify** by loading the graph exactly the way the app does (read-only, `forMobile()`).
4. **Copy** the folder into `app/src/main/assets/graph-cache/`.

Version lock: GraphHopper must stay on **1.0**. Later releases removed the offline/mobile mode and
cannot load a 1.0 graph.

## Regenerating the bundled Islamabad graph

The current graph was built from a highways-only extract of the Islamabad–Rawalpindi metro
(bounds `33.45,72.85,33.95,73.35`) pulled from the Overpass API.

```powershell
# 1. Extract (Overpass returns 429/504 when busy; retry a few times)
$query = '[out:xml][timeout:900][maxsize:1073741824];(way["highway"](33.45,72.85,33.95,73.35););(._;>;);out body;'
[System.IO.File]::WriteAllText("$pwd\metro.overpass", $query, (New-Object System.Text.UTF8Encoding($false)))
curl.exe --data-urlencode "data@metro.overpass" https://overpass-api.de/api/interpreter -o metro.osm

# 2. Import
.\gradlew.bat :tools:graph-builder:run --args="C:\path\to\metro.osm C:\path\to\out\islamabad"

# 3. Verify (must print a distance and instructions)
.\gradlew.bat :tools:graph-builder:verifyGraph --args="C:\path\to\out\islamabad"

# 4. Ship it
Copy-Item C:\path\to\out\islamabad\* app\src\main\assets\graph-cache\ -Force
```

For a whole country, download the Geofabrik `.osm.pbf` instead and crop it with
[osmium-tool](https://osmcode.org/osmium-tool/) (`osmium extract -b minX,minY,maxX,maxY`).

## Other regions

For every area you add to `OfflineRegionCatalog.kt` (used by the offline map downloads), build a
matching graph and merge it into `assets/graph-cache/`. If a graph becomes too large to bundle,
prune the profiles you do not use (`Profile(...)` lines in `GraphBuilder`) before importing.

## Rules that must stay in sync with the app

`GraphBuilder` and `LocalRoutingEngine.kt` must agree on:

- profile names and vehicles: `car` (fastest), `foot` (shortest), `bike` (fastest)
- contraction hierarchies: `car` only
- elevation: off

If they drift, GraphHopper refuses to load the graph on the device.

## Data licence

OpenStreetMap data is © OpenStreetMap contributors, licensed ODbL. Keep the attribution in the
app's about screen when you ship a graph.
