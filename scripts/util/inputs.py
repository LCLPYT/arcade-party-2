import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

import questionary
import toml

from util.common import TOML_FILE

AUTHOR_KEYS = ["person.lclp", "person.bops"]
LIB_CONFIG_FILE = Path("src/lib/resources/configuration.json")

@dataclass
class Inputs:
    game_id: str
    game_name: str
    game_desc: str
    author_key: str
    author: str
    lang: Literal["java", "kotlin"]
    game_type: Literal["ffa", "ffa_elimination", "team", "team_elimination"]
    can_be_finale: bool
    icon: str

def read_inputs() -> Inputs | None:
    while True:
        game_id = questionary.text("Enter minigame id, e.g. 'my_minigame':").ask()
        err = validate_game_id(game_id)
        if err:
            print(f"Error: {err}")
        else:
            break

    game_name = questionary.text("Enter game name:").ask()
    game_desc = questionary.text("Enter game description:").ask()

    key_to_value, value_to_key = load_authors()

    author_value = questionary.select(
        "Select author (if missing, add in scripts/util/inputs.py):",
        choices=list(key_to_value.values())
    ).ask()

    author = value_to_key[author_value]

    lang = questionary.select(
        "Select a programming language:",
        choices=["kotlin", "java"],
        default="kotlin"
    ).ask()

    game_type = questionary.select(
        "Select game type:",
        choices=["ffa", "ffa_elimination", "team", "team_elimination"]
    ).ask()

    can_be_finale = questionary.confirm("Can this game be a finale?").ask()

    while True:
        icon_id = questionary.text("Enter a minecraft item identifier to use as the game icon, e.g. 'stone_bricks':").ask()
        err = validate_icon(icon_id)
        if err:
            print(f"Error: {err}")
        else:
            break

    print("\nSummary:")
    print(f"  Game ID: {game_id}")
    print(f"  Programming Language: {lang}")
    print(f"  Author: {author_value}")
    print(f"  Name: {game_name}")
    print(f"  Description: {game_desc}")
    print(f"  Type: {game_type}")
    print(f"  Can be finale: {can_be_finale}")
    print(f"  Icon: minecraft:{icon_id}")
    confirm = questionary.confirm("Is this correct?").ask()

    if not confirm:
        print("Aborted.")
        return None

    return Inputs(game_id=game_id, game_name=game_name, game_desc=game_desc, author_key=author, author=author_value,
                  lang=lang, game_type=game_type, can_be_finale=can_be_finale, icon=icon_id)


def validate_game_id(game_id: str) -> str | None:
    if not re.match(r"^[a-z_]+$", game_id):
        return "Only lowercase letters and underscores are allowed."
    if TOML_FILE.exists():
        data = toml.load(TOML_FILE)
        for mg in data.get("minigames", []):
            if mg.get("id") == game_id:
                return f"A minigame with id '{game_id}' already exists."
    return None

def validate_icon(icon: str) -> str | None:
    if not re.match(r"^[a-z_]+$", icon):
        return "Icon identifier must be lowercase letters and underscores only."
    return None

def load_authors() -> tuple[dict[str, str], dict[str, str]]:
    if not LIB_CONFIG_FILE.exists():
        raise FileNotFoundError(f"Config file {LIB_CONFIG_FILE} not found")
    with open(LIB_CONFIG_FILE) as f:
        config = json.load(f)

    # keep both mappings
    key_to_value = {k: config.get(k, k) for k in AUTHOR_KEYS}
    value_to_key = {v: k for k, v in key_to_value.items()}

    return key_to_value, value_to_key
