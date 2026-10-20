# core/notifications

| File | Responsibility |
| --- | --- |
| `NotificationHelper.kt` | Creates channels and posts alert notifications. Only `WARNING`/`CRITICAL` alerts inside the hazard radius + 150 km are posted. |
| `WaffleFirebaseMessagingService.kt` | Receives FCM pushes and writes them into Room before notifying. |

The service is declared in `AndroidManifest.xml` as
`.core.notifications.WaffleFirebaseMessagingService` — move the class and the manifest together.