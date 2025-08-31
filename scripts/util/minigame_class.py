from pathlib import Path

from util.common import pascal_case
from util.inputs import Inputs


def create_minigame_class(code_dir: Path, inputs: Inputs):
    class_name = f"{pascal_case(inputs.game_id)}MiniGame"
    instance_class_name = f"{pascal_case(inputs.game_id)}Instance"
    package_path = f"work/lclpnet/ap2/game/{inputs.game_id}"
    file_ext = "kt" if inputs.lang == "kotlin" else "java"

    class_file = code_dir / f"{package_path}/{class_name}.{file_ext}"

    enum_game_type = "FFA" if inputs.game_type == "ffa" or inputs.game_type == "ffa_elimination" else "TEAM"
    author_const = inputs.author_key[1:].replace('.', '_').upper()
    package_java_path = package_path.replace('/', '.')

    if inputs.lang == "kotlin":
        content = f"""package {package_java_path}

import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.DynamicRegistryManager
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class {class_name} : MiniGame {{
    override fun canBeFinale(context: GameStartContext) = {str(inputs.can_be_finale).lower()}
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("{inputs.game_id}")
    override fun getType() = GameType.{enum_game_type}
    override fun getAuthor() = ApConstants."{author_const}"
    override fun getIcon(manager: DynamicRegistryManager) = ItemStack(Items.{inputs.icon.upper()})
    override fun createInstance(gameHandle: MiniGameHandle) = {instance_class_name}(gameHandle)
}}
"""
    else:
        content = f"""package {package_java_path};

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;

public class {class_name} implements MiniGame {{

    @Override
    public @NotNull Identifier getId() {{
        return ApConstants.identifier("{inputs.game_id}");
    }}

    @Override
    public @NotNull GameType getType() {{
        return GameType.{enum_game_type};
    }}

    @Override
    public @NotNull String getAuthor() {{
        return ApConstants."{author_const}";
    }}

    @Override
    public @NotNull ItemStack getIcon(@NotNull DynamicRegistryManager manager) {{
        return new ItemStack(Items.{inputs.icon.upper()});
    }}

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {{
        return {str(inputs.can_be_finale).lower()};
    }}

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {{
        return true;
    }}

    @Override
    public @NotNull MiniGameInstance createInstance(@NotNull MiniGameHandle gameHandle) {{
        return new {instance_class_name}(gameHandle);
    }}
}}
"""

    with open(class_file, "w") as f:
        f.write(content)
