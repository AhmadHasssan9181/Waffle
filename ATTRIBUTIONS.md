# Third-party notices

Waffle's original application code, branding, artwork, screenshots and documentation are
**Copyright © 2026 Ahmad Hassan. All rights reserved.** They are provided for personal,
non-commercial use only under the [Waffle Personal Use License](LICENSE). No commercial use,
redistribution or publication is permitted without written permission.

Waffle also bundles or calls the following third-party data, engines and assets, which remain under
their own licenses. This file is a practical notice list, not legal advice. Keep it in sync with
`app/src/main/assets` before a public release.

## Map and routing

- **MapLibre Native Android** — BSD 2-Clause. https://github.com/maplibre/maplibre-native
- **GraphHopper 1.0** — Apache License 2.0. The offline graph in `assets/graph-cache` is generated
  with GraphHopper 1.0. https://www.graphhopper.com/
- **OpenStreetMap contributors** — map data and the offline routing/place indexes are derived from
  OpenStreetMap, available under the Open Database License (ODbL).
  https://www.openstreetmap.org/copyright
- **MapTiler** — map styles and vector tiles used by the online basemap, subject to MapTiler's
  terms and attribution requirements. https://www.maptiler.com/
- **OpenRouteService (HeiGIT)** — online routing requests. https://openrouteservice.org/
- **Nominatim / OpenStreetMap** — keyless online geocoding fallback.
  https://nominatim.org/
- **OpenCage** — optional online geocoding, subject to OpenCage's terms.
  https://opencagedata.com/
- **TomTom Traffic** — optional traffic-flow samples, subject to TomTom's terms.
  https://developer.tomtom.com/
- **Open-Meteo** — weather data. https://open-meteo.com/

## Emergency feeds

- **USGS Earthquake Hazards Program** — earthquake GeoJSON feed.
  https://earthquake.usgs.gov/
- **GDACS** — global disaster alert RSS feed. https://www.gdacs.org/
- **Wikimedia Commons** — shop photos, fetched at runtime. Each photo keeps its author/licence
  credit from the Commons metadata and is displayed in the shop detail sheet.

## 3D and visual assets

- **Waffle 3D vehicle, bike, motorcycle and pedestrian models** — original Waffle assets,
  generated procedurally by `tools/cars/generate_models.py`. The `.glb` models in `assets/cars`,
  the palette atlas (`colormap.png`), the navigation puck sprites in `res/drawable-nodpi` and the
  12-frame pedestrian walk cycle in `assets/walker` are all produced by that script, so they carry
  the same licence as the rest of Waffle's original artwork.
- **Material Icons** — Apache License 2.0 (Android/Compose Material components).

### Previous third-party models (no longer bundled)

Earlier builds used the Kenney Car Kit (CC0 1.0), a Quaternius motorcycle (CC0) and a Poly by
Google bicycle (CC BY 3.0). Those files have been replaced by the generated models above, so no
third-party model licence applies to the current APK. The credit is kept here for history.

## SDKs and services

- **Google Play services / Firebase** — Google APIs terms and the Firebase terms. Anonymous
  Firebase Auth, Firestore, FCM, Analytics, WorkManager and Room are used.
- **OkHttp, Retrofit, Gson, Kotlinx Serialization** — their respective open-source licences.
- **Coil** — Apache License 2.0.
- **Accompanist** — Apache License 2.0.

If you redistribute Waffle, check the exact licence text shipped with each dependency and preserve
the notices required by the assets you keep in the APK.
