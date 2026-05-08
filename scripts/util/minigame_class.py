from pathlib import Path

from util.common import pascal_case
from util.inputs import Inputs


def create_minigame_class(code_dir: Path, inputs: Inputs):
    class_name = f"{pascal_case(inputs.game_id)}MiniGame"
    instance_class_name = f"{pascal_case(inputs.game_id)}Instance"
    package_path = f"work/lclpnet/ap2/game/{inputs.game_id}"
    class_dir = code_dir / package_path

    class_dir.mkdir(parents=True, exist_ok=True)

    class_file = class_dir / f"{class_name}.kt"

    enum_game_type = "FFA" if inputs.game_type == "ffa" or inputs.game_type == "ffa_elimination" else "TEAM"
    author_const = inputs.author_key.replace('.', '_').upper()
    package_java_path = package_path.replace('/', '.')

    content = f"""package {package_java_path}

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.RegistryAccess
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class {class_name} : MiniGame {{
    override fun canBeFinale(context: GameStartContext) = {str(inputs.can_be_finale).lower()}
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("{inputs.game_id}")
    override fun getType() = GameType.{enum_game_type}
    override fun getAuthor() = ApConstants.{author_const}
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.{inputs.icon.upper()})
    override fun createInstance(gameHandle: MiniGameHandle) = {instance_class_name}(gameHandle)
}}
"""

    with open(class_file, "w") as f:
        f.write(content)
