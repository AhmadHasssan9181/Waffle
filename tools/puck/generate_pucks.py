#!/usr/bin/env python3
"""Generates the navigation pucks and 3D models via tools/cars/generate_models.py."""

from pathlib import Path
import runpy

if __name__ == "__main__":
    script = Path(__file__).resolve().parents[1] / "cars" / "generate_models.py"
    runpy.run_path(str(script), run_name="__main__")
