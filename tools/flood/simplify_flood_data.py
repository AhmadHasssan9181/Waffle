"""Simplify the bundled Copernicus EMS flood GeoJSONs.

The source files are survey-grade vectors with seven decimal places. The app renders them on a
phone map, where 5 m is already below the visible resolution. Simplifying with a 0.00005 degree
(~5 m) tolerance and rounding to five decimals cuts the flood assets from ~237 MB to a few MB
without changing what a driver sees.

Run from the repository root:
    python tools/flood/simplify_flood_data.py
"""
from pathlib import Path
import gzip
import json

from shapely.geometry import mapping, shape

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "disaster_data"
TOLERANCE = 0.00005
DECIMALS = 5


def round_coords(obj):
    if isinstance(obj, (list, tuple)):
        if obj and all(isinstance(v, (int, float)) for v in obj):
            return [round(v, DECIMALS) for v in obj]
        return [round_coords(v) for v in obj]
    return obj


def simplify_file(path: Path) -> tuple[int, int, int, int]:
    before = path.stat().st_size
    before_gz = len(gzip.compress(path.read_bytes()))
    data = json.loads(path.read_text(encoding="utf-8"))
    for feature in data.get("features", []):
        geometry = feature.get("geometry")
        if not geometry:
            continue
        kind = geometry.get("type")
        if kind in ("Polygon", "MultiPolygon", "LineString", "MultiLineString", "GeometryCollection"):
            try:
                simplified = shape(geometry).simplify(TOLERANCE, preserve_topology=True)
                if simplified.is_empty:
                    feature["geometry"] = None
                    continue
                geometry = mapping(simplified)
            except Exception:
                pass
        feature["geometry"] = round_coords(geometry)
    path.write_text(
        json.dumps(data, separators=(",", ":"), ensure_ascii=False),
        encoding="utf-8",
    )
    after = path.stat().st_size
    after_gz = len(gzip.compress(path.read_bytes()))
    return before, after, before_gz, after_gz


def main() -> None:
    total_before = total_after = total_before_gz = total_after_gz = 0
    files = sorted(ASSETS.glob("*.json"))
    for path in files:
        before, after, before_gz, after_gz = simplify_file(path)
        total_before += before
        total_after += after
        total_before_gz += before_gz
        total_after_gz += after_gz
        print(f"{path.name}: {before / 1e6:.1f} -> {after / 1e6:.1f} MB raw")
    print(
        f"TOTAL raw {total_before / 1e6:.1f} -> {total_after / 1e6:.1f} MB, "
        f"gzip {total_before_gz / 1e6:.1f} -> {total_after_gz / 1e6:.1f} MB, "
        f"files={len(files)}"
    )


if __name__ == "__main__":
    main()
