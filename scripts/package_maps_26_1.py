#!/usr/bin/env python3
"""Package converted worlds back into the 26.1 map variant directories.

For each map variant, takes the extracted saves folder (<saves_dir>/<game>-<variant>/),
packs it into world.tar.xz, then copies all files from 1.21.11/ except the original
world archive into 26.1/.

Usage:
  python scripts/package_maps_26_1.py <saves_dir>
  python scripts/package_maps_26_1.py  (uses default Prism saves path)
"""

import json
import shutil
import subprocess
import sys
from pathlib import Path

MAPS_ROOT = Path(__file__).parent.parent / "run" / "assets" / "maps"
AP2_ROOT = MAPS_ROOT / "ap2"
DEFAULT_SAVES = Path.home() / ".local/share/PrismLauncher/instances/26.1.2/minecraft/saves"
V_OLD = "1.21.11"
V_NEW = "26.1"


def package_variant(game: str, variant: str, variant_dir: Path, saves_dir: Path) -> None:
    world_dir = saves_dir / f"{game}-{variant}"
    if not world_dir.exists():
        print(f"  SKIP {game}/{variant}: {world_dir.name} not found in saves")
        return

    v_old_dir = variant_dir / V_OLD
    v_new_dir = variant_dir / V_NEW

    with open(v_old_dir / "map.json") as f:
        source_name = json.load(f).get("source", "world.tar.xz")

    v_new_dir.mkdir(parents=False, exist_ok=True)

    archive = v_new_dir / source_name
    if archive.exists():
        archive.unlink()

    result = subprocess.run(
        ["tar", "-cJ", "-C", str(world_dir), "-f", str(archive), "."],
        capture_output=True,
    )
    if result.returncode != 0:
        print(f"  ERROR {game}/{variant}: {result.stderr.decode().strip()}")
        archive.unlink(missing_ok=True)
        return

    for item in v_old_dir.iterdir():
        if item.name == source_name:
            continue
        dest = v_new_dir / item.name
        if item.is_dir():
            shutil.copytree(item, dest, dirs_exist_ok=True)
        else:
            shutil.copy2(item, dest)

    print(f"  Packaged: {game}/{variant} -> 26.1/")


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
            if not variant_dir.is_dir() or not (variant_dir / V_OLD).is_dir():
                continue
            package_variant(game_dir.name, variant_dir.name, variant_dir, saves_dir)


if __name__ == "__main__":
    main()
