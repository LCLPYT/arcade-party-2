import json
import time
from pathlib import Path

import toml

from util.common import pascal_case, TOML_FILE, BASE_DIR
from util.inputs import Inputs, read_inputs
from util.instance_class import create_instance_class
from util.minigame_class import create_minigame_class

def write_translation_files(inputs: Inputs, resources_dir: Path):
    lang_json = {
        f"game.ap2.{inputs.game_id}": inputs.game_name,
        f"game.ap2.{inputs.game_id}.description": inputs.game_desc
    }

    lang_dir = resources_dir / "lang"

    lang_dir.mkdir(parents=True, exist_ok=True)

    with open(lang_dir / "en_us.json", "w") as f:
        json.dump(lang_json, f, indent=2)


def create_mod_json(inputs: Inputs, resources_dir: Path):
    mod_json = {
        "schemaVersion": 1,
        "id": f"ap2-minigame-{inputs.game_id.replace('_', '-')}",
        "version": "${version}",
        "authors": [inputs.author],
        "license": "AGPL-3.0",
        "environment": "server",
        "entrypoints": {
            "ap2:minigame": []
        },
        "depends": {
            "ap2-lib": "*"
        },
        "custom": {
            "timestamp": int(time.time()),
            "modmenu": {
                "parent": "ap2-minigames"
            }
        }
    }

    # add minigame entry point, depending on the programming language
    game_class = f"work.lclpnet.ap2.game.{inputs.game_id}.{pascal_case(inputs.game_id)}MiniGame"
    if inputs.lang == "kotlin":
        mod_json["entrypoints"]["ap2:minigame"].append({
            "adapter": "kotlin",
            "value": game_class
        })
        mod_json["depends"]["fabric-language-kotlin"] = ">=1.13.5+kotlin.2.2.10"
    else:
        mod_json["entrypoints"]["ap2:minigame"].append(game_class)
        mod_json["depends"]["java"] = ">=21"

    with open(resources_dir / "fabric.mod.json", "w") as f:
        json.dump(mod_json, f, indent=2)


def update_minigames_toml(inputs: Inputs):
    if TOML_FILE.exists():
        data = toml.load(TOML_FILE)
    else:
        data = {}

    if "minigames" not in data:
        data["minigames"] = []

    data["minigames"].append({
        "id": inputs.game_id,
        "type": [inputs.lang]
    })

    with open(TOML_FILE, "w") as f:
        toml.dump(data, f)


def main():
    inputs = read_inputs()

    if inputs is None:
        return

    update_minigames_toml(inputs)

    # Create directories
    game_dir = BASE_DIR / inputs.game_id
    code_dir = game_dir / inputs.lang
    resources_dir = game_dir / "resources"

    code_dir.mkdir(parents=True, exist_ok=True)
    resources_dir.mkdir(parents=True, exist_ok=True)

    create_mod_json(inputs, resources_dir)
    write_translation_files(inputs, resources_dir)

    create_minigame_class(code_dir, inputs)
    create_instance_class(code_dir, inputs)

    print(f"\n✅ Minigame '{inputs.game_name}' ({inputs.game_id}) created successfully.")


if __name__ == "__main__":
    main()
