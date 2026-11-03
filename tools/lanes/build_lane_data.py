#!/usr/bin/env python3
"""Turn an Overpass `out tags geom` response into the small lane sidecar Waffle ships.

The bundled GraphHopper 1.0 graph does not carry lane tags. This sidecar keeps only the
`lanes`/`lanes:forward`/`lanes:backward` values and the way geometry, so the navigation banner
can show a lane-count hint for the next maneuver while staying offline.

Usage:
  python build_lane_data.py metro_lanes.json app/src/main/assets/lane-data-islamabad.json
"""

from __future__ import annotations

import json
import re
import sys


def first_int(value: str | None) -> int | None:
    if not value:
        return None
    match = re.search(r"\d+", value)
    if not match:
        return None
    number = int(match.group(0))
    return number if 1 <= number <= 8 else None


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: build_lane_data.py <overpass.json> <out.json>")

    with open(sys.argv[1], encoding="utf-8") as handle:
        payload = json.load(handle)

    ways = []
    for element in payload.get("elements", []):
        if element.get("type") != "way":
            continue
        tags = element.get("tags") or {}
        lanes = first_int(tags.get("lanes"))
        if lanes is None:
            continue
        geometry = element.get("geometry") or []
        coordinates = [
            [round(point["lat"], 5), round(point["lon"], 5)]
            for point in geometry
            if "lat" in point and "lon" in point
        ]
        if len(coordinates) < 2:
            continue
        ways.append(
            {
                "id": element.get("id"),
                "lanes": lanes,
                "forward": first_int(tags.get("lanes:forward")),
                "backward": first_int(tags.get("lanes:backward")),
                "oneway": tags.get("oneway") in {"yes", "true", "1", "-1"},
                "geometry": coordinates,
            }
        )

    with open(sys.argv[2], "w", encoding="utf-8") as handle:
        json.dump({"ways": ways}, handle, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {len(ways)} lane ways to {sys.argv[2]}")


if __name__ == "__main__":
    main()
