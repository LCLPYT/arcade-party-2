#!/usr/bin/env python3
"""Migrate map repository to versioned subfolder structure.

Moves each map variant's files into a 1.21.11/ subfolder, creates an empty
26.1/ stub, and updates index.json files to use the new variants format.

Reference: run/assets/maps/ap2/aim_master/ (already migrated)
"""

import json
import shutil
from pathlib import Path

MAPS_ROOT = Path(__file__).parent.parent / "run" / "assets" / "maps"
AP2_ROOT = MAPS_ROOT / "ap2"
SKIP_GAMES = {"aim_master"}

V_OLD = "1.21.11"
V_NEW = "26.1"


def make_variants(path_value: str) -> dict:
    return {
        V_OLD: {"path": f"{path_value}/{V_OLD}", "depends": {"minecraft": f"<={V_OLD}"}},
        V_NEW: {"path": f"{path_value}/{V_NEW}", "depends": {"minecraft": f">={V_NEW}"}},
    }


def migrate_variant_folder(variant_dir: Path) -> None:
    """Move all contents of variant_dir into variant_dir/1.21.11/ and create variant_dir/26.1/."""
    v_old_dir = variant_dir / V_OLD
    if v_old_dir.exists():
        print(f"  SKIP (already migrated): {variant_dir}")
        return

    v_old_dir.mkdir()
    for item in list(variant_dir.iterdir()):
        if item.name not in (V_OLD, V_NEW):
            shutil.move(str(item), str(v_old_dir / item.name))

    (variant_dir / V_NEW).mkdir(exist_ok=True)
    print(f"  Migrated: {variant_dir}")


def migrate_index(index_path: Path, resolve_relative_to: Path) -> None:
    """Update an index.json: replace each path field with a variants block.

    resolve_relative_to is the base directory used to locate variant folders on
    disk.  Both relative paths (e.g. "farm") and absolute paths (e.g.
    "/ap2/.../fallen_city") are supported — absolute paths are resolved by
    stripping the leading slash and joining with MAPS_ROOT via resolve_relative_to.
    """
    with open(index_path) as f:
        data = json.load(f)

    changed = False
    for entry in data.get("maps", []):
        if "path" not in entry:
            continue
        path_value = entry.pop("path")
        entry["variants"] = make_variants(path_value)
        variant_dir = resolve_relative_to / path_value.lstrip("/")
        if variant_dir.exists():
            migrate_variant_folder(variant_dir)
        changed = True

    if changed:
        with open(index_path, "w") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"  Updated: {index_path.relative_to(MAPS_ROOT)}")


def main() -> None:
    print("=== Migrating game index files ===")
    for game_dir in sorted(AP2_ROOT.iterdir()):
        if not game_dir.is_dir() or game_dir.name in SKIP_GAMES:
            continue
        index_path = game_dir / "index.json"
        if not index_path.exists():
            continue
        print(f"\n{game_dir.name}/")
        migrate_index(index_path, resolve_relative_to=game_dir)

    print("\n=== Migrating ap2/index.json (preparation) ===")
    migrate_index(AP2_ROOT / "index.json", resolve_relative_to=AP2_ROOT)

    print("\n=== Migrating datapacks/index.json ===")
    migrate_index(MAPS_ROOT / "datapacks" / "index.json", resolve_relative_to=MAPS_ROOT)

    print("\nDone.")


if __name__ == "__main__":
    main()
