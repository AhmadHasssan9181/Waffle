#!/usr/bin/env python3
"""Install the Pakistan map pack on a connected phone.

The pack is far too large to ship in the APK (~700 MB), so it lives in the app's external files
directory and the app picks it up on the next launch. This script pushes every piece and extracts
the fonts and sprite archives on the host, so nothing has to be unzipped on the device.

    python tools/maps/install_offline_pack.py
    python tools/maps/install_offline_pack.py --pack build/release-preview28
    python tools/maps/install_offline_pack.py --serial LMG820UM90ce0806 --verify

Layout written to Android/data/com.noobdev.waffle/files/:

    pakistan_vector.pmtiles   OpenMapTiles basemap
    pakistan_places.pmtiles   Overture POI layer
    places_search.db          FTS5 place index
    fonts/<stack>/<range>.pbf label glyphs
    style/sprite.json         POI icons
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.noobdev.waffle"
DEVICE_DIR = f"/sdcard/Android/data/{PACKAGE}/files"

# The three single-file assets. The archives are handled separately.
FILES = [
    "pakistan_vector.pmtiles",
    "pakistan_places.pmtiles",
    "places_search.db",
]

# archive -> directory it should be expanded into, relative to DEVICE_DIR
ARCHIVES = {
    "fonts.zip": "fonts",
    "sprite.zip": "style",
}


def find_adb() -> str:
    candidate = shutil.which("adb")
    if candidate:
        return candidate
    # Fall back to the SDK path recorded in local.properties.
    props = ROOT / "local.properties"
    if props.is_file():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")
                exe = Path(sdk) / "platform-tools" / ("adb.exe" if sys.platform == "win32" else "adb")
                if exe.is_file():
                    return str(exe)
    sys.exit("adb not found. Install platform-tools or set sdk.dir in local.properties.")


def run(cmd: list[str], dry_run: bool) -> None:
    printable = " ".join(cmd)
    if dry_run:
        print(f"  [dry-run] {printable}")
        return
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"failed: {printable}\n{result.stdout}{result.stderr}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fp:
        for chunk in iter(lambda: fp.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def human(num: int) -> str:
    if num >= 1024 ** 3:
        return f"{num / 1024 ** 3:.1f} GB"
    if num >= 1024 ** 2:
        return f"{num / 1024 ** 2:.0f} MB"
    return f"{num / 1024:.0f} KB"


def manifest_checks(pack: Path) -> dict[str, str]:
    manifest = pack / "MANIFEST.json"
    if not manifest.is_file():
        return {}
    try:
        entries = json.loads(manifest.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}
    return {entry["file"]: entry["sha256"] for entry in entries if "file" in entry}


def extract(zip_path: Path, into: Path) -> Path:
    """Extracts an archive into `into` and returns the directory to push."""
    into.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(into)
    # fonts.zip contains a top-level `fonts/`; sprite.zip is flat.
    nested = into / zip_path.stem
    if nested.is_dir():
        return nested
    target = into / ARCHIVES[zip_path.name]
    if target.is_dir():
        return target
    return into


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--pack",
        type=Path,
        default=ROOT / "build" / "release-preview28",
        help="directory holding the release assets (default: build/release-preview28)",
    )
    parser.add_argument("--serial", help="adb device serial, when more than one is attached")
    parser.add_argument(
        "--verify",
        action="store_true",
        help="check sha256 against MANIFEST.json before pushing",
    )
    parser.add_argument("--dry-run", action="store_true", help="print commands only")
    args = parser.parse_args()

    pack: Path = args.pack.resolve()
    if not pack.is_dir():
        sys.exit(f"pack directory not found: {pack}")

    adb = find_adb()
    serial_args = ["-s", args.serial] if args.serial else []

    missing = [name for name in FILES if not (pack / name).is_file()]
    missing += [name for name in ARCHIVES if not (pack / name).is_file()]
    if missing:
        sys.exit(f"missing in {pack}:\n  " + "\n  ".join(missing))

    if args.verify:
        expected = manifest_checks(pack)
        if not expected:
            print("! no usable MANIFEST.json, skipping checksum verification")
        for name in FILES + list(ARCHIVES):
            want = expected.get(name)
            if not want:
                print(f"! {name}: not listed in MANIFEST.json")
                continue
            got = sha256(pack / name)
            state = "ok" if got == want else "MISMATCH"
            print(f"  {name:26s} {state}")
            if got != want:
                print(f"    manifest {want}")
                print(f"    actual   {got}")

    total = sum((pack / name).stat().st_size for name in FILES)
    print(f"\nPushing to {DEVICE_DIR} ({human(total)} of map data)")

    run([adb, *serial_args, "shell", "mkdir", "-p", DEVICE_DIR], args.dry_run)
    for name in FILES:
        source = pack / name
        print(f"  {name} ({human(source.stat().st_size)})")
        run([adb, *serial_args, "push", str(source), f"{DEVICE_DIR}/{name}"], args.dry_run)

    with tempfile.TemporaryDirectory(prefix="waffle-pack-") as tmp:
        tmp_root = Path(tmp)
        for archive_name, target_dir in ARCHIVES.items():
            print(f"  {archive_name} -> files/{target_dir}/")
            if args.dry_run:
                print(f"  [dry-run] unzip {archive_name}")
                continue
            extracted = extract(pack / archive_name, tmp_root / archive_name)
            # Push as `files/<target_dir>` by pushing the directory into DEVICE_DIR.
            staged = tmp_root / "stage" / target_dir
            if staged.exists():
                shutil.rmtree(staged)
            staged.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(extracted, staged)
            run([adb, *serial_args, "push", str(staged), f"{DEVICE_DIR}/"], args.dry_run)

    print("\nOn device:")
    run([adb, *serial_args, "shell", "ls", "-l", DEVICE_DIR], args.dry_run)
    print(
        "\nDone. Force-stop and reopen Waffle, then check Settings > Offline > Pakistan map pack."
    )


if __name__ == "__main__":
    main()
