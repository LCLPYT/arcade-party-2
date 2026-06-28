from pathlib import Path

from util.common import pascal_case, render_template
from util.inputs import Inputs


def create_minigame_class(code_dir: Path, inputs: Inputs):
    class_name = f"{pascal_case(inputs.game_id)}MiniGame"
    instance_class_name = f"{pascal_case(inputs.game_id)}Instance"
    package_path = f"work/lclpnet/ap2/{inputs.game_id}"
    class_dir = code_dir / package_path

    class_dir.mkdir(parents=True, exist_ok=True)

    is_team = inputs.game_type in ("team", "team_elimination")
    enum_game_type = "TEAM" if is_team else "FFA"
    author_const = inputs.author_key.replace('.', '_').upper()
    package_java_path = package_path.replace('/', '.')

    if inputs.uses_maps:
        factory_name = "MapLevelTeamGameFactory" if is_team else "MapLevelGameFactory"
        factory_import = f"import work.lclpnet.ap2.game.util.{factory_name}\n"
        factory_expr = f"{factory_name}(::{instance_class_name})"
        uses_maps_line = ""
    else:
        factory_import = ""
        factory_expr = f"{pascal_case(inputs.game_id)}Factory()"
        uses_maps_line = "    override val usesMaps = false\n"

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
        factory_import=factory_import,
        factory_expr=factory_expr,
        uses_maps_line=uses_maps_line,
    )

    (class_dir / f"{class_name}.kt").write_text(content)
