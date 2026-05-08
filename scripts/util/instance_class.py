from pathlib import Path

from util.common import pascal_case
from util.inputs import Inputs


def create_instance_class(code_dir: Path, inputs: Inputs):
    package_path = f"work/lclpnet/ap2/game/{inputs.game_id}"
    instance_class_name = f"{pascal_case(inputs.game_id)}Instance"
    class_dir = code_dir / package_path

    class_dir.mkdir(parents=True, exist_ok=True)

    instance_file = class_dir / f"{instance_class_name}.kt"
    package_java_path = package_path.replace('/', '.')

    if inputs.game_type == "ffa":
        content = get_ffa_instance_class(package_java_path, instance_class_name)
    elif inputs.game_type == "ffa_elimination":
        content = get_ffa_elimination_instance_class(package_java_path, instance_class_name)
    elif inputs.game_type == "team":
        content = get_team_instance_class(package_java_path, instance_class_name)
    elif inputs.game_type == "team_elimination":
        content = get_team_elimination_instance_class(package_java_path, instance_class_name)
    else:
        print(f"Unknown game type {inputs.game_type}")
        return

    with open(instance_file, "w") as f:
        f.write(content)


def get_ffa_instance_class(package: str, class_name: str) -> str:
    return f"""package {package}

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef

class {class_name}(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle) {{

    private val data = IntScoreDataContainer(PlayerRef::create)

    override fun getData() = data

    override fun prepare() {{

    }}

    override fun go() {{

    }}
}}
"""


def get_ffa_elimination_instance_class(package: str, class_name: str) -> str:
    return f"""package {package}

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.EliminationGameInstance

class {class_name}(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {{

    override fun prepare() {{

    }}

    override fun go() {{

    }}
}}
"""


def get_team_instance_class(package: str, class_name: str) -> str:
    return f"""package {package}

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.TeamGameInstance
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer

class {class_name}(gameHandle: MiniGameHandle) : TeamGameInstance(gameHandle) {{

    private val data = IntScoreDataContainer(this::createReference)

    override fun getData() = data

    override fun prepare() {{

    }}

    override fun go() {{

    }}
}}
"""


def get_team_elimination_instance_class(package: str, class_name: str) -> str:
    return f"""package {package}

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance

class {class_name}(gameHandle: MiniGameHandle) : TeamEliminationGameInstance(gameHandle) {{

    override fun prepare() {{

    }}

    override fun go() {{

    }}
}}
"""
