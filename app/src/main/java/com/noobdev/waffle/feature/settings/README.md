# feature/settings

One scrolling settings screen, reached from the Home and Nearby headers (it is not a tab).

Sections: Appearance (System/Light/Dark + map style), Offline maps, Notifications (master + per
disaster), Emergency numbers (real `ACTION_DIAL` intents), About (version, data sources, clear
cached places).

`clearAllTables()` on the shops database runs on `Dispatchers.IO` and never touches downloaded map
packs or the alert cache.

`MapStylePicker` is also used from the map screen, so keep it public.