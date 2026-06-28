from pathlib import Path

from util.common import pascal_case, render_template
from util.inputs import Inputs

INSTANCE_TEMPLATES = {
    "ffa": "FFAInstance.kt.tmpl",
    "ffa_elimination": "FFAEliminationInstance.kt.tmpl",
    "team": "TeamInstance.kt.tmpl",
    "team_elimination": "TeamEliminationInstance.kt.tmpl",
}

# Mapless games have no dedicated base class per elimination type, so they collapse
# to a single ffa / team scaffold based on the game type prefix.
MAPLESS_INSTANCE_TEMPLATES = {
    "ffa": "MaplessFFAInstance.kt.tmpl",
    "team": "MaplessTeamInstance.kt.tmpl",
}

MAPLESS_FACTORY_TEMPLATES = {
    "ffa": "MaplessFFAFactory.kt.tmpl",
    "team": "MaplessTeamFactory.kt.tmpl",
}


def create_instance_class(code_dir: Path, inputs: Inputs):
    package_path = f"work/lclpnet/ap2/{inputs.game_id}"
    package = package_path.replace('/', '.')
    instance_class_name = f"{pascal_case(inputs.game_id)}Instance"
    class_dir = code_dir / package_path

    class_dir.mkdir(parents=True, exist_ok=True)

    is_team = inputs.game_type in ("team", "team_elimination")
    type_key = "team" if is_team else "ffa"

    if inputs.uses_maps:
        template_name = INSTANCE_TEMPLATES.get(inputs.game_type)
    else:
        template_name = MAPLESS_INSTANCE_TEMPLATES.get(type_key)

    if template_name is None:
        print(f"Unknown game type {inputs.game_type}")
        return

    content = render_template(
        template_name,
        package=package,
        class_name=instance_class_name,
    )

    (class_dir / f"{instance_class_name}.kt").write_text(content)

    if not inputs.uses_maps:
        factory_class_name = f"{pascal_case(inputs.game_id)}Factory"
        factory_content = render_template(
            MAPLESS_FACTORY_TEMPLATES[type_key],
            package=package,
            class_name=pascal_case(inputs.game_id),
            instance_class_name=instance_class_name,
        )

        (class_dir / f"{factory_class_name}.kt").write_text(factory_content)
