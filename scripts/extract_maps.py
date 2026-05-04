#!/usr/bin/env python3
"""Extract 1.21.11 world archives into a Minecraft saves directory.

For each map variant under run/assets/maps/ap2/, reads the map.json source
field and extracts the corresponding archive into:
  <saves_dir>/<game>-<variant>/

Usage:
  python scripts/extract_maps.py <saves_dir>
  python scripts/extract_maps.py  (uses default Prism saves path)
"""

import json
import subprocess
import sys
from pathlib import Path

MAPS_ROOT = Path(__file__).parent.parent / "run" / "assets" / "maps"
AP2_ROOT = MAPS_ROOT / "ap2"
DEFAULT_SAVES = Path.home() / ".local/share/PrismLauncher/instances/26.1.2/minecraft/saves"
VERSION = "1.21.11"


def extract_variant(game: str, variant: str, version_dir: Path, saves_dir: Path) -> None:
    map_json = version_dir / "map.json"
    if not map_json.exists():
        print(f"  SKIP {game}/{variant}: no map.json")
        return

    with open(map_json) as f:
        data = json.load(f)

    source = data.get("source")
    if not source:
        print(f"  SKIP {game}/{variant}: no source field in map.json")
        return

    archive = version_dir / source
    if not archive.exists():
        print(f"  SKIP {game}/{variant}: archive not found ({source})")
        return

    target = saves_dir / f"{game}-{variant}"
    if target.exists():
        print(f"  SKIP {game}/{variant}: {target.name} already exists")
        return

    target.mkdir(parents=True)
    result = subprocess.run(
        ["tar", "-xJ", "-f", str(archive), "-C", str(target)],
        capture_output=True,
    )
    if result.returncode != 0:
        print(f"  ERROR {game}/{variant}: {result.stderr.decode().strip()}")
        target.rmdir()
    else:
        print(f"  Extracted: {game}/{variant} -> {target.name}/")


def main() -> None:
    saves_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SAVES
    if not saves_dir.exists():
        print(f"Saves directory not found: {saves_dir}")
        sys.exit(1)

    print(f"Saves dir: {saves_dir}\n")

    for game_dir in sorted(AP2_ROOT.iterdir()):
        if not game_dir.is_dir():
            continue
        for variant_dir in sorted(game_dir.iterdir()):
            if not variant_dir.is_dir():
                continue
            version_dir = variant_dir / VERSION
            if not version_dir.is_dir():
                continue
            extract_variant(game_dir.name, variant_dir.name, version_dir, saves_dir)


if __name__ == "__main__":
    main()
