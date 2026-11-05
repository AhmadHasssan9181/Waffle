# Waffle privacy notes

This is a practical description of what the current preview build does. It should be reviewed by
the project owner and, for a Play Store release, replaced with a hosted privacy policy that matches
the final build.

## Data kept on the device

- **Location** is used to centre the map, plan routes, measure distance to nearby alerts, and
  prefill the Rescue screen. The last fix is stored in app-private DataStore and is also written
  into the optional saved navigation session.
- **Saved route/trip state** (destination, stops, route geometry, profile) is stored in an
  app-private JSON file so a trip can resume after process death. It expires after 24 hours.
- **Alert and shop caches** are stored in app-private Room databases.
- **Preferences** (theme, units, vehicle icon, voice, alert types) are stored in DataStore.
- Backup is disabled in the current manifest (`android:allowBackup="false"`), so these app-private
  files are not copied into Android cloud/device backup.

## Network requests

When the phone has connectivity, the app may send:

- the current position and destination to **OpenRouteService** for online routing;
- search text and coordinates to **OpenCage**, with **Nominatim/OSM** as fallback;
- coordinates to **TomTom** for traffic flow;
- coordinates to **Open-Meteo** for weather;
- requests to **MapTiler** for map styles/tiles and offline map downloads;
- requests to **USGS**, **GDACS**, **Wikimedia Commons**, and **OpenStreetMap/Overpass**;
- anonymous Firebase Auth, Firestore alert reads, FCM token registration, and Firebase Analytics
  events.

These providers receive the request data described above and may log it under their own policies.
No API key or service credential should be treated as secret when it is embedded in a public APK;
production deployments should proxy paid services through a backend.

## Signals and permissions

- The foreground navigation service uses location while a trip is active and shows a notification.
- The floating map requires the "display over other apps" permission.
- Notifications require Android 13+ notification permission.
- The app does not request background location in the current preview; navigation uses a foreground
  service with the while-in-use location permission.

## Contact

Open an issue in the project repository for privacy or data-deletion requests. This preview does
not provide a user account or a server-side deletion endpoint.
