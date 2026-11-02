// Generates header banner for Waffle (1200x400)
import { writeFileSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";

const OUT = resolve("D:/Waffle/assets");
mkdirSync(OUT, { recursive: true });

const MONO = `'Consolas','JetBrains Mono',monospace`;
const DISP = `'Arial Black',Impact,sans-serif`;
const SANS = `'Segoe UI',system-ui,sans-serif`;

function waffleBanner() {
  const W = 1200, H = 400;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#0a1226"/>
    <stop offset="0.6" stop-color="#0d1b38"/>
    <stop offset="1" stop-color="#081024"/>
  </linearGradient>
  <radialGradient id="glow" cx="0.8" cy="0.5" r="0.6">
    <stop offset="0" stop-color="#3d5afe" stop-opacity="0.35"/>
    <stop offset="0.6" stop-color="#00d0ff" stop-opacity="0.12"/>
    <stop offset="1" stop-color="#000000" stop-opacity="0"/>
  </radialGradient>
  <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
    <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#ffffff" stroke-opacity="0.04" stroke-width="1"/>
  </pattern>
  <style>
    .mono { font-family: ${MONO}; font-weight: 700; }
    .disp { font-family: ${DISP}; font-weight: 900; }
    .sans { font-family: ${SANS}; }
    .blink { animation: blink 1.2s steps(1,end) infinite; }
    @keyframes blink { 50% { opacity: 0; } }
  </style>
</defs>

<!-- BG -->
<rect width="${W}" height="${H}" fill="url(#bg)"/>
<rect width="${W}" height="${H}" fill="url(#grid)"/>
<ellipse cx="960" cy="200" rx="360" ry="240" fill="url(#glow)"/>

<!-- Top Status Line -->
<g transform="translate(60, 48)">
  <rect x="0" y="0" width="145" height="26" rx="6" fill="#3d5afe" fill-opacity="0.22" stroke="#3d5afe" stroke-width="1.5"/>
  <circle cx="14" cy="13" r="4" fill="#00d0ff" class="blink"/>
  <text x="26" y="17" class="mono" font-size="11" letter-spacing="1.5" fill="#7fe3ff">AUTONOMOUS OPS</text>

  <text x="160" y="17" class="mono" font-size="11" letter-spacing="1.5" fill="#8a9ba8">// FLIGHT COMPANION &#183; OFFLINE NAVIGATION &#183; GEMINI AI</text>
</g>

<!-- Big Title -->
<g transform="translate(56, 146)">
  <text class="disp" font-size="88" letter-spacing="-1" fill="#3d5afe" opacity="0.3" x="4" y="4">W.A.F.F.L.E.</text>
  <text class="disp" font-size="88" letter-spacing="-1" fill="#ffffff">W.A.F.F.L.E.</text>
  <text class="mono" font-size="16" letter-spacing="3" fill="#00d0ff" x="650" y="-30">v2.1</text>
</g>

<!-- Subtitle & Specs -->
<g transform="translate(60, 202)">
  <text class="sans" font-size="20" font-weight="600" fill="#e2e8f0">Wireless Autonomous Flying Fleet for Life Emergencies</text>
  <text class="sans" font-size="15" fill="#94a3b8" y="28">Resilient Android Navigation &amp; Ground Companion Computer for Post-Disaster Operations</text>

  <!-- Spec Pills -->
  <g transform="translate(0, 75)">
    <!-- Pill 1 -->
    <rect x="0" y="0" width="165" height="32" rx="7" fill="#ffffff" fill-opacity="0.06" stroke="#ffffff" stroke-opacity="0.15" stroke-width="1.2"/>
    <text x="82" y="20" text-anchor="middle" class="mono" font-size="11" fill="#f8fafc">Jetpack Compose</text>

    <!-- Pill 2 -->
    <rect x="177" y="0" width="145" height="32" rx="7" fill="#ffffff" fill-opacity="0.06" stroke="#ffffff" stroke-opacity="0.15" stroke-width="1.2"/>
    <text x="249" y="20" text-anchor="middle" class="mono" font-size="11" fill="#f8fafc">MapLibre GL</text>

    <!-- Pill 3 -->
    <rect x="334" y="0" width="170" height="32" rx="7" fill="#ffffff" fill-opacity="0.06" stroke="#ffffff" stroke-opacity="0.15" stroke-width="1.2"/>
    <text x="419" y="20" text-anchor="middle" class="mono" font-size="11" fill="#f8fafc">GraphHopper Offline</text>

    <!-- Pill 4 -->
    <rect x="516" y="0" width="165" height="32" rx="7" fill="#3d5afe" fill-opacity="0.2" stroke="#3d5afe" stroke-width="1.2"/>
    <text x="598" y="20" text-anchor="middle" class="mono" font-size="11" fill="#7fe3ff">Google Gemini AI</text>
  </g>

  <!-- Metric highlight -->
  <text x="0" y="145" class="mono" font-size="12" letter-spacing="1.5" fill="#64748b">ZERO-INTERNET ROUTING &#183; REAL-TIME FCM BROADCASTS &#183; MVVM + COROUTINES</text>
</g>

<!-- Right Side: Graphic Visualizer -->
<g transform="translate(860, 75)">
  <!-- Outer Box -->
  <rect x="0" y="0" width="280" height="260" rx="14" fill="#0c162d" stroke="#ffffff" stroke-opacity="0.12" stroke-width="2"/>
  
  <!-- Radar concentric circles -->
  <circle cx="140" cy="130" r="40" fill="none" stroke="#3d5afe" stroke-width="1.5" opacity="0.4"/>
  <circle cx="140" cy="130" r="75" fill="none" stroke="#3d5afe" stroke-width="1.5" stroke-dasharray="4 4" opacity="0.3"/>
  <circle cx="140" cy="130" r="105" fill="none" stroke="#3d5afe" stroke-width="1.5" opacity="0.2"/>

  <!-- Flight Path simulation -->
  <path d="M 40 180 C 80 120, 110 160, 150 90 S 210 60, 240 80" fill="none" stroke="#00d0ff" stroke-width="2.5" stroke-dasharray="6 4"/>
  <circle cx="240" cy="80" r="6" fill="#ff7a1a"/>
  <circle cx="240" cy="80" r="12" fill="none" stroke="#ff7a1a" stroke-width="1.5"/>

  <!-- Companion Node Point -->
  <circle cx="150" cy="90" r="5" fill="#7ed321"/>
  <text x="162" y="94" class="mono" font-size="9" fill="#7ed321" font-weight="700">WAYPOINT 04</text>

  <!-- Crosshairs -->
  <path d="M 140 20 V 40 M 140 220 V 240 M 20 130 H 40 M 240 130 H 260" stroke="#00d0ff" stroke-width="1.5" opacity="0.6"/>

  <!-- Footer Tag -->
  <rect x="15" y="215" width="250" height="30" rx="6" fill="#000000" fill-opacity="0.5"/>
  <text x="25" y="234" class="mono" font-size="10" fill="#94a3b8">FLEET: <tspan fill="#7fe3ff">4 ACTIVE</tspan> &#183; LINK: <tspan fill="#7ed321">MAVLINK OK</tspan></text>
</g>

<!-- Accent Lines -->
<rect x="0" y="396" width="${W}" height="4" fill="#3d5afe"/>
</svg>`;
}

writeFileSync(resolve(OUT, "waffle-banner.svg"), waffleBanner(), "utf8");
console.log("Waffle banner created!");
