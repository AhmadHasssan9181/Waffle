# feature/shops/data

Shops have their own storage stack, separate from the alert database.

| File | Responsibility |
| --- | --- |
| `Shop.kt` | UI model, `ShopCategory` metadata (label, icon, tint, OSM tags). |
| `ShopEntity.kt` | Room entity + mapping to/from `Shop`. |
| `ShopDao.kt` | Bounding-box read, upsert, photo columns, stale cleanup. |
| `ShopDatabase.kt` | `waffle_shops.db`, version 1, singleton. |
| `ShopRepository.kt` | Overpass query, retry, cache freshness, amenity ranking. |
| `ShopPhotoRepository.kt` | Wikimedia Commons geosearch and photo-to-shop matching. |

**Overpass**
- POST UTF-8 **without a BOM** (`application/x-www-form-urlencoded` via OkHttp `FormBody`).
- Endpoints in order: `overpass-api.de`, `overpass.kumi.systems`.
- Rate limits show up as HTTP 429/504; the repository backs off and retries once.
- Cap at 150 elements and keep only named places.

**Photos**
One Commons `generator=geosearch` call per area (radius 1600 m, 60 images, namespace 6).
An image is attached to the nearest shop within **70 m**; the closest unused image wins.
`photoCredit` must contain `Artist · Licence` and must be rendered wherever the photo is.
If Commons returns nothing, the UI draws a category-tinted icon instead — never a broken image.