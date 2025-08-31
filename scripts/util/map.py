import json
import re
from dataclasses import dataclass
from pathlib import Path

import questionary

from util.common import validate_icon, load_authors


@dataclass
class MapOptions:
    id: str
    name: str
    authors: list[str]
    icon: str
    spawn: tuple[int, int, int]


def read_map_options() -> MapOptions | None:
    if not questionary.confirm("Do you want to create a map?").ask():
        return None

    while True:
        map_id = questionary.text("Enter map id, e.g. 'my_map':").ask()
        err = validate_icon(map_id)
        if err:
            print(f"Error: {err}")
        else:
            break

    map_name = questionary.text("Enter map id, e.g. 'my_map':").ask()

    key_to_value, value_to_key = load_authors(only_devs=False)

    authors = questionary.checkbox(
        "Enter map authors (if missing, add in scripts/util/inputs.py):",
        choices=list(key_to_value.values())
    ).ask()

    author_refs = [f"@{value_to_key[val]}" for val in authors]

    while True:
        icon_id = questionary.text("Enter a minecraft item identifier to use as the map icon, e.g. 'stone_bricks':").ask()
        err = validate_icon(icon_id)
        if err:
            print(f"Error: {err}")
        else:
            break

    spawn = ask_vec3i("Enter spawn position, e.g. x, y, z:")

    return MapOptions(id=map_id, name=map_name, authors=author_refs, icon=icon_id, spawn=spawn)


def ask_vec3i(prompt: str) -> tuple[int, int, int]:
    def validate(text):
        try:
            split = [p.strip() for p in text.split(",")]
            if len(split) != 3:
                return "Please provide exactly 3 numbers separated by commas."
            [int(p) for p in split]  # validate ints
        except ValueError:
            return "All coordinates must be valid integers."
        return True

    raw = questionary.text(prompt, validate=validate).ask()
    parts = [int(p.strip()) for p in raw.split(",")]
    return parts[0], parts[1], parts[2]


def validate_map_id(map_id: str) -> str | None:
    if not re.match(r"^[a-z_]+$", map_id):
        return "Map id must be lowercase letters and underscores only."
    return None


def add_map(game_id: str, opts: MapOptions):
    cfg_dir = Path("run/config")

    add_maps_source_to_json(cfg_dir)

    game_dir = cfg_dir / f"assets/maps/ap2/{game_id}"
    game_dir.mkdir(parents=True, exist_ok=True)

    if not add_to_index(opts, game_dir):
        return

    create_map(opts, game_dir)

    print(f"✅ Map created successfully. Copy a world to {game_dir / opts.id / "world"} to use it.")


def create_map(opts: MapOptions, game_dir: Path):
    map_dir = game_dir / opts.id

    map_dir.mkdir(parents=True, exist_ok=True)

    json_file = map_dir / "map.json"

    if json_file.exists():
        return

    obj = {
        "source": "world",
        "spawn": opts.spawn
    }

    with open(json_file) as f:
        json.dump(obj, f)

    world_dir = map_dir / "world"
    world_dir.mkdir(parents=True, exist_ok=True)


def add_to_index(opts: MapOptions, game_dir: Path):
    index_file = game_dir / "index.json"

    if index_file.exists():
        with open(index_file) as f:
            index = json.load(f)
    else:
        index = {
            "maps": []
        }

    for entry in index["maps"]:
        if entry["path"] == opts.id:
            print(f"Map with path {opts.id} already exists, skipping...")
            return False

    index["maps"].append({
        "path": opts.id,
        "name": opts.name,
        "authors": opts.authors,
        "icon": f"minecraft:{opts.icon}"
    })

    with open(index_file) as f:
        json.dump(index, f)

    return True


def add_maps_source_to_json(cfg_dir: Path):
    ap2_cfg = cfg_dir / "ap2/config.json"

    ap2_cfg.mkdir(parents=True, exist_ok=True)

    if ap2_cfg.exists():
        with open(ap2_cfg) as f:
            cfg = json.load(f)
    else:
        cfg = {
            "maps_source": ["https://assets.lclpnet.work/release/maps/"]
        }

    maps_source = cfg.get("maps_source", [])

    if "assets/maps" in maps_source:
        return

    maps_source.append("assets/maps")
    cfg["maps_source"] = maps_source

    with open(ap2_cfg) as f:
        json.dump(cfg, f)
