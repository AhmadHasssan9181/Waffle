<p align="center">
  <img
    alt="W.A.F.F.L.E. Header"
    src="./assets/waffle-readme-header.png"
    width="100%"
  />
</p>

<h1 align="center">W.A.F.F.L.E. — Wireless Autonomous Flying Fleet for Life Emergencies</h1>

<p align="center">
  <strong>Offline-first Android navigation console and field-awareness station built for zero-connectivity operations.</strong>
</p>

<p align="center">
  <a href="https://github.com/AhmadHasssan9181/Waffle/releases/tag/v1.0.0-preview.25">
    <img src="https://img.shields.io/badge/Download_APK-Preview_25-F59E0B?style=for-the-badge&logo=android&logoColor=white" alt="Download Preview 25" />
  </a>
  &nbsp;
  <a href="https://github.com/AhmadHasssan9181/Waffle/releases">
    <img src="https://img.shields.io/badge/Releases-Changelog-0F172A?style=for-the-badge&logo=github&logoColor=white" alt="Releases" />
  </a>
  &nbsp;
  <a href="https://github.com/AhmadHasssan9181/Waffle/issues">
    <img src="https://img.shields.io/badge/Feedback-Open_Issue-0F766E?style=for-the-badge&logo=github&logoColor=white" alt="Open an issue" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-7.0%2B_(API_24)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 7.0+" />
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.1" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/MapLibre-Vector_GL-00A8CC?style=flat-square&logo=maplibre&logoColor=white" alt="MapLibre" />
  <img src="https://img.shields.io/badge/GraphHopper-Offline_Engine-D97706?style=flat-square" alt="GraphHopper" />
  <img src="https://img.shields.io/badge/License-Personal_Use-B45309?style=flat-square" alt="Personal use only" />
</p>

> [!IMPORTANT]
> **Preview 25 (`v1.0.0-preview.25`) is a personal-use preview.** The APK is signed for direct installation. Waffle is free for personal, non-commercial use; commercial use, redistribution, and republication are reserved. See [License](#license--legal).

---

## <img src="./assets/icons/screen.svg" width="22" height="22" align="absmiddle" alt="" /> &nbsp;Ground Console Interface

**W.A.F.F.L.E.** transforms an Android device into a self-contained field navigation and situational-awareness console—pairing local **MapLibre Native** vector rendering, an embedded **GraphHopper** road-routing engine, 3D vehicle telemetry, and live **USGS / GDACS** disaster feeds.

<table>
  <tr>
    <td align="center" width="25%">
      <img src="./assets/screenshots/home.png" width="225" alt="Waffle Command Home" />
      <br /><br />
      <b>Command Home</b>
      <br />
      <sub>Nearest seismic/hazard status, local weather, and rapid field actions</sub>
    </td>
    <td align="center" width="25%">
      <img src="./assets/screenshots/navigation.png" width="225" alt="Waffle 3D Turn-by-Turn Navigation" />
      <br /><br />
      <b>3D Turn-by-Turn HUD</b>
      <br />
      <sub>3D vehicle tracking, voice guidance, route trimming, and live ETA</sub>
    </td>
    <td align="center" width="25%">
      <img src="./assets/screenshots/test-mode.png" width="225" alt="Waffle Test Mode Route Simulator" />
      <br /><br />
      <b>Test Mode Simulator</b>
      <br />
      <sub>Variable acceleration, braking, lane hints, and off-route rerouting</sub>
    </td>
    <td align="center" width="25%">
      <img src="./assets/screenshots/offline-map.png" width="225" alt="Waffle Zero-Signal Vector Map" />
      <br /><br />
      <b>Zero-Signal Vector Map</b>
      <br />
      <sub>Cached regional street tiles with live USGS &amp; GDACS alert radii</sub>
    </td>
  </tr>
</table>

---

## <img src="./assets/icons/route.svg" width="22" height="22" align="absmiddle" alt="" /> &nbsp;Hybrid Routing &amp; Zero-Signal Failover

When cellular towers drop or map tile servers hit rate limits, Waffle switches seamlessly from cloud APIs to on-device flash storage without blanking the map or dropping an active turn-by-turn session.

<p align="center">
  <img
    alt="Figure 01 — Hybrid Routing and Failover Pipeline"
    src="./assets/figures/routing-pipeline.svg"
    width="100%"
  />
</p>

<table>
  <tr>
    <td width="33%" valign="top">
      <b>Dual-Engine Route Planner</b>
      <br />
      <sub>Queries <b>OpenRouteService</b> with alternate routes and road-avoidance filters when online, and falls back to the bundled <b>GraphHopper</b> road graph when disconnected.</sub>
    </td>
    <td width="33%" valign="top">
      <b>Off-Route Watchdog</b>
      <br />
      <sub>Continuously projects live GPS coordinates onto the active route polyline, triggering an automatic recalculation and Android TTS voice prompt on deviation.</sub>
    </td>
    <td width="33%" valign="top">
      <b>Style &amp; Quota Resilience</b>
      <br />
      <sub>Monitors MapTiler tile responses and mounts your completed local vector pack automatically if the network drops or returns <code>HTTP 429</code>.</sub>
    </td>
  </tr>
</table>

---

## <img src="./assets/icons/compass.svg" width="22" height="22" align="absmiddle" alt="" /> &nbsp;Multi-Modal 3D Vehicles &amp; Route Simulator

Every travel mode applies its own road-network weights, speed assumptions, camera pitch, and custom 3D map model—backed by a built-in physics simulator (`Test Mode`) for rehearsing routes before heading into the field.

<p align="center">
  <img
    alt="Figure 02 — Multi-Modal 3D Vehicle Profiles and Route Simulator"
    src="./assets/figures/travel-modes.svg"
    width="100%"
  />
</p>

---

## <img src="./assets/icons/map.svg" width="22" height="22" align="absmiddle" alt="" /> &nbsp;Offline Regional Map Packs

Regional vector packs are managed in the **Offline maps** screen and downloaded in the background via resumable `WorkManager` jobs:

<p align="center">
  <img
    alt="Figure 03 — Regional Vector Tile Packs and On-Device Routing Coverage"
    src="./assets/figures/offline-coverage.svg"
    width="100%"
  />
</p>

---

## <img src="./assets/icons/layers.svg" width="22" height="22" align="absmiddle" alt="" /> &nbsp;System Architecture

Waffle runs a unidirectional `StateFlow` architecture centered around an application-scoped `MapSessionState` so moving between **Home**, **Map**, **Nearby**, **Alerts**, **Rescue**, or the **Floating Map / PiP** overlay preserves your active navigation and simulation state.

<p align="center">
  <img
    alt="Figure 04 — Unidirectional StateFlow and App-Scoped Navigation Session"
    src="./assets/figures/system-architecture.svg"
    width="100%"
  />
</p>

---

## <img src="./assets/icons/download.svg" width="22" height="22" align="absmiddle" alt="" /> &nbsp;Install Preview 25

<p align="center">
  <a href="https://github.com/AhmadHasssan9181/Waffle/releases/tag/v1.0.0-preview.25">
    <img src="https://img.shields.io/badge/Get_Preview_25_APK-arm64--v8a_%7C_armeabi--v7a-F59E0B?style=for-the-badge&logo=android&logoColor=white" alt="Get Preview 25 APK" />
  </a>
</p>

1. Open the [**Preview 25 Release**](https://github.com/AhmadHasssan9181/Waffle/releases/tag/v1.0.0-preview.25).
2. Download the APK matching your phone (`arm64-v8a` for modern 64-bit devices, `armeabi-v7a` for older 32-bit hardware).
3. Allow installation from your browser or file manager when prompted by Android.
4. Grant **Location** permission for navigation and **Notifications** for background turn-by-turn and offline pack download progress.

---

## <img src="./assets/icons/shield.svg" width="22" height="22" align="absmiddle" alt="" /> &nbsp;License &amp; Legal

**Free for personal, non-commercial use. All other rights reserved.**

Waffle is free to download and use for personal, non-commercial purposes. Commercial use, redistribution, sublicensing, or publication of modified or unmodified copies is not permitted without prior written permission from the author.

- **License Terms:** [LICENSE](LICENSE)
- **Privacy Policy:** [PRIVACY.md](PRIVACY.md)
- **Third-Party Attributions:** [ATTRIBUTIONS.md](ATTRIBUTIONS.md)

---

<p align="center">
  <img src="./branding/mark.svg" width="42" height="42" alt="Waffle Mark" />
  <br />
  <sub>
    Crafted by <a href="https://github.com/AhmadHasssan9181"><b>Ahmad Hassan</b></a> &nbsp;·&nbsp;
    <a href="https://github.com/AhmadHasssan9181/Waffle/releases">Releases</a> &nbsp;·&nbsp;
    <a href="https://github.com/AhmadHasssan9181/Waffle/issues">Issue Tracker</a>
  </sub>
</p>
