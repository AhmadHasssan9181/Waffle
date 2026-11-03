#!/usr/bin/env python3
"""Build a compact offline road-name index from Overpass `out tags center` responses.

The shipped offline_places.json contains POIs only, so a search like "Mall Road Rawalpindi"
never finds the road. This index stores one searchable row per named highway way with its
bounding-box centre. The router snaps the chosen point to the nearest road.

Usage:
  python build_road_data.py roads_q1.json roads_q4.json app/src/main/assets/offline_roads.json
"""

from __future__ import annotations

import json
import re
import sys


def normalized(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip().lower()


def main() -> None:
    if len(sys.argv) < 3:
        raise SystemExit("usage: build_road_data.py <overpass.json>... <out.json>")

    output = sys.argv[-1]
    rows = []
    seen = set()

    for source in sys.argv[1:-1]:
        with open(source, encoding="utf-8") as handle:
            payload = json.load(handle)
        for element in payload.get("elements", []):
            if element.get("type") != "way":
                continue
            tags = element.get("tags") or {}
            center = element.get("center") or {}
            if "lat" not in center or "lon" not in center:
                continue
            name_en = (tags.get("name:en") or "").strip()
            name_local = (tags.get("name") or "").strip()
            ref = (tags.get("ref") or "").strip()
            display = name_en or name_local
            if not display:
                continue
            highway = (tags.get("highway") or "road").strip()
            # One row per road name within roughly ten kilometres, so a long road does not
            # fill the result list with duplicate segments.
            key = (normalized(display), round(center["lat"], 1), round(center["lon"], 1))
            if key in seen:
                continue
            seen.add(key)
            alt = " ".join(part for part in [name_local, name_en, ref] if part)
            rows.append(
                {
                    "name": display,
                    "alt": alt if alt != display else "",
                    "address": ref or highway.replace("_", " ").title(),
                    "lat": round(center["lat"], 6),
                    "lon": round(center["lon"], 6),
                    "type": "road",
                }
            )

    rows.sort(key=lambda row: (normalized(row["name"]), row["name"]))
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(rows, handle, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {len(rows)} road rows to {output}")


if __name__ == "__main__":
    main()
