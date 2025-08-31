from pathlib import Path

BASE_DIR = Path("src/minigames")
TOML_FILE = BASE_DIR / "minigames.toml"

def pascal_case(name: str) -> str:
    return "".join(word.capitalize() for word in name.split("_"))
