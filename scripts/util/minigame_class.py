from pathlib import Path

from util.common import pascal_case, render_template
from util.inputs import Inputs


def create_minigame_class(code_dir: Path, inputs: Inputs):
    class_name = f"{pascal_case(inputs.game_id)}MiniGame"
    instance_class_name = f"{pascal_case(inputs.game_id)}Instance"
    package_path = f"work/lclpnet/ap2/game/{inputs.game_id}"
    class_dir = code_dir / package_path

    class_dir.mkdir(parents=True, exist_ok=True)

    enum_game_type = "FFA" if inputs.game_type == "ffa" or inputs.game_type == "ffa_elimination" else "TEAM"
    author_const = inputs.author_key.replace('.', '_').upper()
    package_java_path = package_path.replace('/', '.')

    content = render_template(
        "MiniGame.kt.tmpl",
        package=package_java_path,
        class_name=class_name,
        can_be_finale=str(inputs.can_be_finale).lower(),
        game_id=inputs.game_id,
        game_type=enum_game_type,
        author_const=author_const,
        icon=inputs.icon.upper(),
        instance_class_name=instance_class_name,
    )

    (class_dir / f"{class_name}.kt").write_text(content)
