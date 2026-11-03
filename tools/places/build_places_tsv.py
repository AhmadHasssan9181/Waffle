#!/usr/bin/env python3
"""Convert the large offline_places.json into a line-oriented TSV for fast Android loading.

JSONObject parsing of ~86k rows takes long enough that a user can type a search before the
offline index is ready. A tab-separated line per place parses in a fraction of the time.

Usage:
  python build_places_tsv.py tools/places/offline_places.json app/src/main/assets/offline_places.tsv
"""

from __future__ import annotations

import json
import sys


def clean(value: object, fallback: str = "") -> str:
    if value is None:
        return fallback
    return str(value).replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: build_places_tsv.py <places.json> <places.tsv>")

    with open(sys.argv[1], encoding="utf-8") as handle:
        places = json.load(handle)

    written = 0
    with open(sys.argv[2], "w", encoding="utf-8", newline="\n") as handle:
        for item in places:
            name = clean(item.get("name"))
            if not name:
                continue
            address = clean(item.get("address"), "Pakistan") or "Pakistan"
            lat = item.get("lat")
            lng = item.get("lng")
            if lat is None or lng is None:
                continue
            kind = clean(item.get("type"), "offline") or "offline"
            handle.write(f"{name}\t{address}\t{lat}\t{lng}\t{kind}\n")
            written += 1
    print(f"wrote {written} places to {sys.argv[2]}")


if __name__ == "__main__":
    main()
