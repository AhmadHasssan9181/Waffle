# Waffle 🧇

**Waffle** is a modern Android application built with Kotlin and Jetpack Compose. It serves as a comprehensive tool focused on offline mapping, location-based intelligence, disaster/flood alert tracking, and navigation. 

Designed to be resilient, Waffle integrates offline routing, live map rendering, AI-powered features (via Gemini), and real-time push notifications, keeping users informed and safely navigated even in challenging connectivity environments.

---

## 🌟 Key Features

*   **Modern Interactive UI:** Entirely built using **Jetpack Compose** following Material Design 3 guidelines.
*   **Detailed Mapping & Navigation:** Integrated with **MapLibre GL** for live maps and **GraphHopper** for robust offline routing and navigation.
*   **Offline First:** Built-in offline geocoding and map processing so features work when connectivity drops.
*   **Smart AI Assistance:** Integrates **Google Gemini** for intelligent data analysis and prompt handling.
*   **Real-time Alerts & Intel:** Tracks vital data (e.g., flood intelligence) via REST APIs (Retrofit) and caches data locally for seamless access.
*   **Push Notifications:** Firebase Cloud Messaging (FCM) integration ensures users receive immediate broadcast alerts safely and reliably.
*   **Background Location Services:** Continuously tracks user location to deliver context-aware alerts and optimized routing.

---

## 🛠️ Tech Stack & Architecture

This project strictly follows modern Android development practices using **Kotlin** and an **MVVM (Model-View-ViewModel)** architectural pattern.

**Core Android & UI:**
*   **Kotlin (2.1.20)**
*   **Jetpack Compose:** Declarative UI toolkit.
*   **AndroidX Navigation Compose:** For single-activity screen routing.
*   **ViewModel & Lifecycle:** Managing UI-related data synchronously with Android lifecycles.
*   **DataStore:** Asynchronous, safer preference storage mechanism.
*   **Kotlin Coroutines & Flow:** Managing asynchronous tasks and observing reactive data streams.

**Networking & Data Sync:**
*   **Retrofit2 & OkHttp3:** Type-safe HTTP client for API requests and logging.
*   **Kotlinx Serialization:** JSON parsing for API payloads.
*   **Room Database (2.6.1):** SQLite object mapping library for robust local caching (`AlertDatabase`, `AlertDao`).

**Mapping & Location Services:**
*   **MapLibre GL Android SDK:** Open-source map rendering.
*   **Google Play Services Location:** Accurate GPS fetching.
*   **GraphHopper Core:** Offline routing engine.
*   **Mapbox Android Maps Utils:** Supplementary geographical calculations.

**Backend & Cloud (Firebase):**
*   **Firebase BOM (34.11.0)**
*   **Cloud Messaging (FCM):** Handling incoming device alerts.
*   **Firestore & Auth:** Authentication and scalable cloud data synchronization.
*   **Crashlytics & Analytics:** Monitoring user behavior and stability.

---

## 📂 Project Structure

```text
Waffle/
│
├── app/
│   ├── build.gradle.kts           # App-level build configurations
│   ├── google-services.json       # Firebase credentials (ensure this exists locally!)
│   └── src/main/java/com/noobdev/waffle/
│       ├── MainActivity.kt        # Entry point of the Compose app
│       ├── WaffleApplication.kt   # Context/Global state initialization
│       ├── Navigation.kt          # Compose UI NavHost definitions
│       │
│       ├── api/                   # Retrofit API clients and Offline Geocoders
│       ├── database/              # Room DAOs, Entities (e.g., AlertEntity) and TypeConverters
│       ├── instances/             # Remote service instances (Gemini AI, Retrofit base builders)
│       ├── navigation/            # Route simulation and Camera transitions (GraphHopper/MapLibre)
│       ├── notifications/         # Firebase Messaging Services & system notification wrappers
│       ├── routing/               # Offline Routing Manager orchestrations
│       ├── ui/                    # Compose Themes, Colors, Typography, and shared Components
│       ├── utils/                 # PreferencesManager and helper utilities
│       └── viewmodel/             # ViewModels interacting with Repositories and exposing StateFlow 
│
├── gradle/
│   └── libs.versions.toml         # Centralized dependency catalog
├── build.gradle.kts               # Root-level build configuration
└── settings.gradle.kts            # Module inclusions and repository setups
```

---

## 🚀 Getting Started

### Prerequisites

1.  **Android Studio** (Koala or Ladybug - Recommended due to AGP `8.12.3`).
2.  **Java Development Kit (JDK) 17+** (Required for Android Gradle Plugin 8.0+).
3.  **Firebase Project credentials**. You need a valid `google-services.json` placed inside `app/` folder to successfully build the app without Firebase initialization errors.

### Local Installation

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/YourUsername/Waffle.git
    cd Waffle
    ```
2.  **Check Firebase Configuration:**
    Ensure you place your `google-services.json` file inside the `app/` directory.

3.  **MapLibre Setup:**
    Check `MainActivity.kt` and resource configs. Ensure your API keys (`WellKnownTileServer` keys) are properly provisioned in your `local.properties` or environment variables if needed.

4.  **Sync Gradle & Build:**
    Open the project in Android Studio, allow Gradle to sync the dependencies from the `libs.versions.toml`, and hit **Run (Shift + F10)**.

### Permissions Required
Upon fresh install, the app will request the following core system permissions:
*   Internet / Network State
*   Location (Foreground, Background, Coarse, and Fine)
*   Post Notifications (Android 13+)

---

## ©️ License / Author

**Author:** noobdev  
Please reach out to the project maintainer for questions involving distribution, API limitations (like GraphHopper/MapLibre API constraints), or making broad architectural optimizations.


---

## Where things live

The Kotlin package is organised by layer first, then by feature. Every folder has its own
`README.md` describing the files and the invariants that are easy to break.

```
com.noobdev.waffle
├── core/          platform plumbing, no UI
│   ├── navigation/    geometry, blast radius, hazards, turn-by-turn
│   ├── routing/       offline GraphHopper first, ORS fallback
│   ├── storage/       asset copying, free space
│   ├── prefs/         DataStore settings
│   ├── network/       connectivity + metered checks
│   └── notifications/ channels + FCM
├── data/          shared data layer
│   ├── model/         DisasterAlert, StatusLevel
│   ├── local/         Room alert cache (waffle_database)
│   └── remote/        OpenCage, Open-Meteo, ORS, Gemini, offline geocoder
├── design/        the visual language
│   ├── theme/         colours, Manrope type, shapes, AppTheme
│   ├── components/    shared cards, badges, buttons, spacing
│   └── brand/         WaffleMark / Wordmark / Lockup
└── feature/       one folder per capability
    ├── home/  map/  shops/  alerts/  rescue/
    ├── offline/  settings/
    └── flood/  hazards/   (helpers, not tabs)
```

Start with `feature/README.md` for the feature map, `design/README.md` for the palette rules, and
`core/routing/README.md` before touching anything GraphHopper-related.

### Design language

Warm neutrals (ink + cream), honey as the single brand accent, one desaturated sky accent for
live/GPS, and red/green only when they mean danger/safe. No rainbow theme picker: the app follows
System, Light or Dark. Manrope is bundled as five static weights (SIL OFL).

### Building

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Offline routing graph check:

```bash
./gradlew :tools:graph-builder:verifyGraph --args="app/src/main/assets/graph-cache"
```

### Non-obvious invariants

- **GraphHopper stays on 1.0.** It is the last version with offline support; the bundled graph was
  built with it.
- **Shops have their own database** (`waffle_shops.db`) so shop schema changes never risk the
  alert cache.
- **One shop cache feeds two surfaces.** Map pins and the Nearby list read the same repository and
  open the same `ShopDetailSheet`; never fork that model.
- **Map style `-1` means auto** (Dark basemap in dark UI, Streets in light). Any explicit index wins.
- **Alerts are read from Room**, not Firestore, so Home and Alerts agree and work offline.
- **The rescue screen has no backend.** It dials real numbers and hands a prefilled SMS to the
  phone; it never claims a request was dispatched.
- **Photos need attribution.** Wikimedia Commons `Artist · Licence` is stored on the row and shown
  with the image.
- **Do not convert the brand SVG to a VectorDrawable.** The launcher and in-app marks are PNGs
  rasterised from `branding/mark.svg`.