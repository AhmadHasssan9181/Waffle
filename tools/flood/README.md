# tools/flood

The Copernicus EMS flood vectors in `app/src/main/assets/disaster_data/` are survey-grade
GeoJSON: seven decimal places, thousands of tiny polygons, and hundreds of megabytes of text.
The phone map only needs a few metres of accuracy, so `simplify_flood_data.py` applies a
Douglas-Peucker simplification (0.00005 degrees, about 5 m) and rounds coordinates to five
decimals.

It also writes compact JSON (no pretty-print whitespace), which is what the app parses at runtime.

```powershell
python tools/flood/simplify_flood_data.py
```

Current result:

- raw assets: ~249 MB -> ~80 MB
- gzip-equivalent: ~48 MB -> ~20 MB
- APK flood payload: roughly 60 MB -> roughly 25 MB

The script keeps properties and feature IDs, drops geometries that collapse to nothing, and is
safe to re-run. The source data is Copernicus EMSR838; keep the Copernicus attribution in the
app's About/attribution screen.
