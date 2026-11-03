# Offline places index

The original `offline_places.json` is ~6 MB and takes long enough to parse with `JSONObject` that a
user can type a search before the index is ready. Waffle ships `offline_places.tsv` instead:
one tab-separated row per place (`name`, `address`, `lat`, `lng`, `type`). The loader reads it line
by line and lowercases names, which is fast on a phone.

The JSON source stays here for regeneration; it is not packaged in the APK.

```powershell
python tools/places/build_places_tsv.py `
  tools/places/offline_places.json `
  app/src/main/assets/offline_places.tsv
```

`OfflineGeocoder` combines these places with `offline_roads.json`, tokenises the query and scores
name, address, road refs and alternate names. If nothing matches offline and the phone is online,
the search card falls back to OpenCage.

Data © OpenStreetMap contributors, ODbL.
