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

- **Kenney Car Kit** — CC0 1.0. Cars in `assets/cars`.
  https://kenney.nl/assets/car-kit
- **Quaternius** — CC0 motorcycle model.
  https://quaternius.com/
- **Poly by Google** — bicycle model, CC-BY 3.0; attribution is required.
  https://poly.pizza/
- **Waffle hoodie walker** — project-specific asset; walk frames in `assets/walker`.
- **Material Icons** — Apache License 2.0 (Android/Compose Material components).

## SDKs and services

- **Google Play services / Firebase** — Google APIs terms and the Firebase terms. Anonymous
  Firebase Auth, Firestore, FCM, Analytics, WorkManager and Room are used.
- **OkHttp, Retrofit, Gson, Kotlinx Serialization** — their respective open-source licences.
- **Coil** — Apache License 2.0.
- **Accompanist** — Apache License 2.0.

If you redistribute Waffle, check the exact licence text shipped with each dependency and preserve
the notices required by the assets you keep in the APK.
