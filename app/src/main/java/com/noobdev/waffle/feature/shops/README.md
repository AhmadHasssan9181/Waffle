# feature/shops

"Nearby": what is open around me — food, groceries, pharmacies, fuel, banks, hotels.

| File | Responsibility |
| --- | --- |
| `ShopsScreen.kt` | Header, search, category chips, radius picker, shop cards. |
| `ShopDetailSheet.kt` | Shared detail sheet (Nearby + map pin): photo/placeholder, hours, address, call, website, directions. |
| `ShopsViewModel.kt` | Location, cache-first load, filtering, radius changes, photo attachment. |
| `data/Shop.kt` | `Shop` model + `ShopCategory` (label, icon, tint, OSM tag mapping). |
| `data/ShopEntity.kt` | Room entity in the **separate** `waffle_shops.db`. |
| `data/ShopDao.kt` | Bounding-box query, upsert, photo update, old-row cleanup. |
| `data/ShopDatabase.kt` | Room singleton, version 1. |
| `data/ShopRepository.kt` | Overpass fetch + retries + cache policy. |
| `data/ShopPhotoRepository.kt` | Wikimedia Commons geosearch, licence capture, nearest-shop matching. |

**Why a separate database?** `waffle_database` (alerts) uses destructive migrations. Shops change
schema more often; keeping them apart means shop work never risks the alert cache.

**Cache policy**

| Rule | Value |
| --- | --- |
| Default radius | 1.5 km |
| Cache considered fresh | 14 days |
| Rows older than | 60 days are deleted |
| Overpass endpoints | `overpass-api.de`, then `overpass.kumi.systems` |
| Result cap | 150 elements |

If Overpass fails but cached rows exist, the repository returns the cached rows — never an error.

**Photos**
OSM has no photos. We ask Commons for geotagged images around the user (one request for the whole
area, not one per shop), then attach each image to the nearest shop within 70 m. `photoCredit`
stores `Artist · Licence` and is shown on the image. Do not drop the credit: Commons licences
require attribution.

Only **named** OSM elements are kept. Unnamed nodes are noise in a list and usually lack hours or
address data.

Attribution shown in the UI: "Places © OpenStreetMap contributors · photos from Wikimedia Commons".