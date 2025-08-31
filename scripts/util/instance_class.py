from pathlib import Path

from util.common import pascal_case
from util.inputs import Inputs


def create_instance_class(code_dir: Path, inputs: Inputs):
    file_ext = "kt" if inputs.lang == "kotlin" else "java"
    package_path = f"work/lclpnet/ap2/game/{inputs.game_id}"
    instance_class_name = f"{pascal_case(inputs.game_id)}Instance"
    class_dir = code_dir / package_path

    class_dir.mkdir(parents=True, exist_ok=True)

    instance_file = class_dir / f"{instance_class_name}.{file_ext}"
    package_java_path = package_path.replace('/', '.')

    if inputs.game_type == "ffa":
        content = get_ffa_instance_class(inputs, package_java_path, instance_class_name)
    elif inputs.game_type == "ffa_elimination":
        content = get_ffa_elimination_instance_class(inputs, package_java_path, instance_class_name)
    elif inputs.game_type == "team":
        content = get_team_instance_class(inputs, package_java_path, instance_class_name)
    elif inputs.game_type == "team_elimination":
        content = get_team_elimination_instance_class(inputs, package_java_path, instance_class_name)
    else:
        print(f"Unknown game type {inputs.game_type}")
        return

    with open(instance_file, "w") as f:
        f.write(content)


def get_ffa_instance_class(inputs: Inputs, package: str, class_name: str) -> str:
    if inputs.lang == "kotlin":
        return f"""package {package}

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef

class {class_name}(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle) {{
    
    val data = IntScoreDataContainer(PlayerRef::create)
    
    override fun getData() = data

    override fun prepare() {{
        
    }}

    override fun ready() {{
        
    }}
}}
"""
    else:
        return f"""package {package};

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;

public class {class_name} extends FFAGameInstance {{
    
    private final IntScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new IntScoreDataContainer<>(PlayerRef::create);

    public {class_name}(MiniGameHandle gameHandle) {{
        super(gameHandle);
    }}

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {{
        return data;
    }}

    @Override
    protected void prepare() {{

    }}

    @Override
    protected void ready() {{

    }}
}}
"""


def get_ffa_elimination_instance_class(inputs: Inputs, package: str, class_name: str) -> str:
    if inputs.lang == "kotlin":
        return f"""package {package}

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.EliminationGameInstance

class {class_name}(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {{

    override fun prepare() {{

    }}

    override fun ready() {{

    }}
}}
"""
    else:
        return f"""package {package};

import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;

public class {class_name} extends EliminationGameInstance {{

    public {class_name}(MiniGameHandle gameHandle) {{
        super(gameHandle);
    }}

    @Override
    protected void prepare() {{

    }}

    @Override
    protected void ready() {{

    }}
}}
"""


def get_team_instance_class(inputs: Inputs, package: str, class_name: str) -> str:
    if inputs.lang == "kotlin":
        return f"""package {package}

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.TeamGameInstance
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer

class {class_name}(gameHandle: MiniGameHandle) : TeamGameInstance(gameHandle) {{

    val data = IntScoreDataContainer(this::createReference)

    override fun getData() = data

    override fun prepare() {{

    }}

    override fun ready() {{

    }}
}}
"""
    else:
        return f"""package {package};

import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.impl.game.TeamGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;

public class {class_name} extends TeamGameInstance {{
    
    private final IntScoreDataContainer<Team, TeamRef> data = new IntScoreDataContainer<>(this::createReference);

    public {class_name}(MiniGameHandle gameHandle) {{
        super(gameHandle);
    }}

    @Override
    protected DataContainer<Team, TeamRef> getData() {{
        return data;
    }}

    @Override
    protected void prepare() {{

    }}

    @Override
    protected void ready() {{

    }}
}}
"""


def get_team_elimination_instance_class(inputs: Inputs, package: str, class_name: str) -> str:
    if inputs.lang == "kotlin":
        return f"""package {package}

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance

class {class_name}(gameHandle: MiniGameHandle) : TeamEliminationGameInstance(gameHandle) {{

    override fun prepare() {{

    }}

    override fun ready() {{

    }}
}}
"""
    else:
        return f"""package {package};

import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;

public class {class_name} extends TeamEliminationGameInstance {{

    public {class_name}(MiniGameHandle gameHandle) {{
        super(gameHandle);
    }}

    @Override
    protected void prepare() {{

    }}

    @Override
    protected void ready() {{

    }}
}}
"""
