import json
import re
import time
from pathlib import Path

from util.common import pascal_case, BASE_DIR, render_template
from util.inputs import Inputs, read_inputs
from util.instance_class import create_instance_class
from util.map import add_map
from util.minigame_class import create_minigame_class

SETTINGS_GRADLE = Path("settings.gradle")


def write_translation_files(inputs: Inputs, resources_dir: Path):
    lang_json = {
        f"name": inputs.game_name,
        f"description": inputs.game_desc
    }

    lang_dir = resources_dir / "lang"
    lang_dir.mkdir(parents=True, exist_ok=True)

    with open(lang_dir / "en_us.json", "w") as f:
        json.dump(lang_json, f, indent=2)


def create_mod_json(inputs: Inputs, resources_dir: Path):
    game_class = f"work.lclpnet.ap2.{inputs.game_id}.{pascal_case(inputs.game_id)}MiniGame"
    mod_json = {
        "schemaVersion": 1,
        "id": f"ap2-minigame-{inputs.game_id.replace('_', '-')}",
        "version": "${version}",
        "authors": [inputs.author],
        "license": "MIT",
        "environment": "server",
        "entrypoints": {
            "ap2:minigame": [{"adapter": "kotlin", "value": game_class}]
        },
        "depends": {
            "ap2-lib": "*",
            "fabric-language-kotlin": "*"
        },
        "custom": {
            "timestamp": int(time.time()),
            "modmenu": {
                "parent": "ap2-minigames"
            }
        }
    }

    with open(resources_dir / "fabric.mod.json", "w") as f:
        json.dump(mod_json, f, indent=2)


def create_build_gradle(game_dir: Path):
    (game_dir / "build.gradle.kts").write_text(render_template("build.gradle.kts.tmpl"))


def update_settings_gradle(game_id: str):
    text = SETTINGS_GRADLE.read_text()

    start_marker = "def minigames = ["
    start_idx = text.index(start_marker) + len(start_marker)
    end_idx = text.index("]", start_idx)

    block = text[start_idx:end_idx]
    existing = re.findall(r'"([^"]+)"', block)
    existing.append(game_id)
    existing.sort()

    new_block = "\n" + "".join(f'        "{g}",\n' for g in existing)
    new_text = text[:start_idx] + new_block + text[end_idx:]

    SETTINGS_GRADLE.write_text(new_text)


def main():
    inputs = read_inputs()

    if inputs is None:
        return

    update_settings_gradle(inputs.game_id)

    game_dir = BASE_DIR / inputs.game_id
    code_dir = game_dir / "src/main/kotlin"
    resources_dir = game_dir / "src/main/resources"

    code_dir.mkdir(parents=True, exist_ok=True)
    resources_dir.mkdir(parents=True, exist_ok=True)

    create_build_gradle(game_dir)
    create_mod_json(inputs, resources_dir)
    write_translation_files(inputs, resources_dir)

    create_minigame_class(code_dir, inputs)
    create_instance_class(code_dir, inputs)

    if inputs.map is not None:
        add_map(inputs.game_id, inputs.map)

    print(f"\n✅ Minigame '{inputs.game_name}' ({inputs.game_id}) created successfully. Refresh the Gradle project in IntelliJ to use it. Happy coding! 💫")


if __name__ == "__main__":
    main()
