# feature/rescue

SOS without a backend.

| File | Responsibility |
| --- | --- |
| `RescueScreen.kt` | Call 1122, show exact coordinates, compose an SMS, open historical Copernicus flood maps, list national numbers, first-aid reminders. |

**There is no dispatch server.** The screen deliberately does not say "help is on the way" or
simulate a request. It does two things that actually work with a weak signal:

1. `ACTION_DIAL` to 1122 (the call is placed by the phone app, so no CALL_PHONE permission).
2. `ACTION_SENDTO smsto:` with a prefilled message containing coordinates and a Google Maps link.
   If no SMS app exists it falls back to `ACTION_SEND`, then to the clipboard.

`Coords` are read from `FusedLocationProviderClient.lastLocation`. If there is no fix the message
says so instead of sending a stale position.

**Flood maps:** the Flood maps strip lists all nine EMSR838 sectors. Selecting one requests a
`MapTarget` with a `Sector` and opens the Map tab; the map loads the depth/water/extent layers and
shows the legend. This is historical Copernicus data, not a live forecast.

Do not reintroduce a fake submit button.