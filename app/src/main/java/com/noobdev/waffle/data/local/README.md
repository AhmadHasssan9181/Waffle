# data/local

Room database `waffle_database` (version 2).

| File | Responsibility |
| --- | --- |
| `AppDatabase.kt` | Singleton, `fallbackToDestructiveMigration()`, DAO accessor. |
| `AlertEntity.kt` | Persisted alert + `toDomainModel()` / `DisasterAlert.toEntity()`. |
| `AlertDao.kt` | Alert queries and the observable flow used by Home and Alerts. |
| `AlertRepository.kt` | Higher-level alert reads/writes. |
| `Converters.kt` | Room type converters (maps, enums). |
| `WaffleApi.kt`, `WaffleClient.kt`, `WaffleModels.kt` | Legacy Retrofit endpoint, kept for reference. |

**Do not add shop tables here.** Shops use their own database (`feature/shops/data/ShopDatabase`,
`waffle_shops.db`) so the alert schema never needs a migration for shop changes.