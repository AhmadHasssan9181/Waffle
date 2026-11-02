// Generates genuine high-fidelity header for Waffle (1200x420)
// using the actual waffle-quadcopter branding from mark.svg
import { writeFileSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const markContent = readFileSync("D:/Waffle/branding/mark.svg", "utf8");
// Extract the inner SVG content from mark.svg
const innerDefsMatch = markContent.match(/<defs>([\s\S]*?)<\/defs>/);
const innerDefs = innerDefsMatch ? innerDefsMatch[1] : "";
const innerBodyMatch = markContent.match(/<\/defs>([\s\S]*?)<\/svg>/);
const innerBody = innerBodyMatch ? innerBodyMatch[1] : "";

const W = 1200, H = 420;

const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}">
<defs>
  ${innerDefs}
  <radialGradient id="header-bg" cx="50%" cy="30%" r="90%">
    <stop offset="0%" stop-color="#FFFDF9"/>
    <stop offset="50%" stop-color="#FAF4E8"/>
    <stop offset="100%" stop-color="#F2E6CE"/>
  </radialGradient>
  <pattern id="header-dots" width="24" height="24" patternUnits="userSpaceOnUse">
    <circle cx="2" cy="2" r="1.5" fill="#78350f" opacity="0.08"/>
  </pattern>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@700;800;900&amp;family=JetBrains+Mono:wght@600;700&amp;display=swap');
    .title { font-family: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif; font-weight: 900; }
    .mono { font-family: 'JetBrains Mono', Consolas, monospace; font-weight: 700; }
    .sans { font-family: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif; font-weight: 700; }
  </style>
</defs>

<!-- Background -->
<rect width="${W}" height="${H}" fill="url(#header-bg)"/>
<rect width="${W}" height="${H}" fill="url(#header-dots)"/>

<!-- Top Status Bar -->
<g transform="translate(60, 50)">
  <rect x="0" y="0" width="168" height="30" rx="7" fill="#f59e0b" fill-opacity="0.16" stroke="#b45309" stroke-width="1.8"/>
  <circle cx="16" cy="15" r="4.5" fill="#d97706"/>
  <text x="30" y="20" class="mono" font-size="12" letter-spacing="1.5" fill="#92400e">AUTONOMOUS UAV</text>

  <text x="186" y="20" class="mono" font-size="12" letter-spacing="1.5" fill="#78350f" opacity="0.75">// FLIGHT COMPANION &#183; OFFLINE NAVIGATION</text>
</g>

<!-- Left Typography Block -->
<g transform="translate(60, 155)">
  <!-- Shadow offset title -->
  <text x="4" y="4" class="title" font-size="82" letter-spacing="-1.5" fill="#78350f" opacity="0.12">W.A.F.F.L.E.</text>
  <text x="0" y="0" class="title" font-size="82" letter-spacing="-1.5" fill="#0f172a">W.A.F.F.L.E.</text>
  
  <text x="0" y="42" class="sans" font-size="22" fill="#0f172a" opacity="0.9">Wireless Autonomous Flying Fleet for Life Emergencies</text>
  <text x="0" y="72" class="sans" font-size="16" fill="#78350f" opacity="0.8">Offline Android Companion Station &#183; MapLibre Vector GL &#183; GraphHopper</text>

  <!-- Spec Badges -->
  <g transform="translate(0, 115)">
    <rect x="0" y="0" width="158" height="34" rx="8" fill="#ffffff" stroke="#0f172a" stroke-width="2"/>
    <text x="79" y="22" text-anchor="middle" class="mono" font-size="12" fill="#0f172a">Jetpack Compose</text>

    <rect x="170" y="0" width="138" height="34" rx="8" fill="#ffffff" stroke="#0f172a" stroke-width="2"/>
    <text x="239" y="22" text-anchor="middle" class="mono" font-size="12" fill="#0f172a">MapLibre GL</text>

    <rect x="320" y="0" width="168" height="34" rx="8" fill="#ffffff" stroke="#0f172a" stroke-width="2"/>
    <text x="404" y="22" text-anchor="middle" class="mono" font-size="12" fill="#0f172a">GraphHopper Offline</text>

    <rect x="500" y="0" width="150" height="34" rx="8" fill="#fef3c7" stroke="#b45309" stroke-width="2"/>
    <text x="575" y="22" text-anchor="middle" class="mono" font-size="12" fill="#92400e">Gemini AI Client</text>
  </g>

  <text x="0" y="195" class="mono" font-size="11.5" letter-spacing="1.5" fill="#78350f" opacity="0.7">PX4 COMPANION &#183; ZERO-WIFI TURN-BY-TURN &#183; DISASTER TELEMETRY</text>
</g>

<!-- Right Side: The ACTUAL Waffle Mark Drone Artwork -->
<g transform="translate(710, -25) scale(0.92)">
  ${innerBody}
</g>

<!-- Clean border -->
<rect x="0" y="0" width="${W}" height="${H}" fill="none" stroke="#0f172a" stroke-width="6"/>
</svg>`;

writeFileSync("D:/Waffle/assets/waffle-readme-header.svg", svg, "utf8");
console.log("waffle-readme-header.svg generated successfully!");
