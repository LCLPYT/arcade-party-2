from pathlib import Path

from util.common import pascal_case, render_template
from util.inputs import Inputs

INSTANCE_TEMPLATES = {
    "ffa": "FFAInstance.kt.tmpl",
    "ffa_elimination": "FFAEliminationInstance.kt.tmpl",
    "team": "TeamInstance.kt.tmpl",
    "team_elimination": "TeamEliminationInstance.kt.tmpl",
}


def create_instance_class(code_dir: Path, inputs: Inputs):
    package_path = f"work/lclpnet/ap2/game/{inputs.game_id}"
    instance_class_name = f"{pascal_case(inputs.game_id)}Instance"
    class_dir = code_dir / package_path

    class_dir.mkdir(parents=True, exist_ok=True)

    template_name = INSTANCE_TEMPLATES.get(inputs.game_type)
    if template_name is None:
        print(f"Unknown game type {inputs.game_type}")
        return

    content = render_template(
        template_name,
        package=package_path.replace('/', '.'),
        class_name=instance_class_name,
    )

    (class_dir / f"{instance_class_name}.kt").write_text(content)
