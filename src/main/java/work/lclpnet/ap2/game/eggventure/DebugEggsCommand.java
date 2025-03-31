package work.lclpnet.ap2.game.eggventure;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.tags.PlayerHeadTags;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;
import work.lclpnet.kibu.inv.type.KibuInventory;

import static net.minecraft.server.command.CommandManager.literal;


public class DebugEggsCommand implements KibuCommand {

    private final Logger logger;

    public DebugEggsCommand(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:eggs")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(this::showEggsInventory));
    }

    private int showEggsInventory(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        var inv = new KibuInventory(6, Text.literal("Eggs"));

        var headEntries = player.getServerWorld().getRegistryManager()
                .getOrThrow(ApRegistries.PLAYER_HEAD)
                .iterateEntries(PlayerHeadTags.EASTER_EGGS);

        int i = 0;

        for (var entry : headEntries) {
            int slot = i++;

            if (slot >= inv.size()) continue;

            ItemStack stack = entry.value().createStack();
            stack.set(DataComponentTypes.ITEM_NAME, Text.literal(entry.getIdAsString()));

            inv.setStack(slot, stack);
        }

        if (i > inv.size()) {
            logger.warn("There {} eggs registered, but the debug inventory can only show {} eggs", i, inv.size());
        }

        player.openHandledScreen(inv);

        return 0;
    }
}
