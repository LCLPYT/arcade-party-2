import re
import json
from pathlib import Path

DEVS = ["person.lclp", "person.bops"]

BASE_DIR = Path("src/minigames")
TOML_FILE = BASE_DIR / "minigames.toml"
LIB_CONFIG_FILE = Path("src/lib/resources/configuration.json")

def pascal_case(name: str) -> str:
    return "".join(word.capitalize() for word in name.split("_"))


def validate_icon(icon: str) -> str | None:
    if not re.match(r"^[a-z_]+$", icon):
        return "Icon identifier must be lowercase letters and underscores only."
    return None

def load_authors(only_devs: bool) -> tuple[dict[str, str], dict[str, str]]:
    if not LIB_CONFIG_FILE.exists():
        raise FileNotFoundError(f"Config file {LIB_CONFIG_FILE} not found")
    with open(LIB_CONFIG_FILE) as f:
        config = json.load(f)

    # keep both mappings
    select_keys = DEVS if only_devs else config.keys()
    key_to_value = {k: config.get(k, k) for k in select_keys}
    value_to_key = {v: k for k, v in key_to_value.items()}

    return key_to_value, value_to_key
