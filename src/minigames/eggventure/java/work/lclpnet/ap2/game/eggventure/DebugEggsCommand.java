package work.lclpnet.ap2.game.eggventure;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.tags.PlayerHeadTags;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;
import work.lclpnet.kibu.inv.type.KibuInventory;

import static net.minecraft.commands.Commands.literal;


public class DebugEggsCommand implements KibuCommand {

    private final Logger logger;

    public DebugEggsCommand(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:eggs")
                .requires(s -> s.hasPermission(2))
                .executes(this::showEggsInventory));
    }

    private int showEggsInventory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        var inv = new KibuInventory(6, Component.literal("Eggs"));

        var headEntries = player.level().registryAccess()
                .lookupOrThrow(ApRegistries.PLAYER_HEAD)
                .getTagOrEmpty(PlayerHeadTags.EASTER_EGGS);

        int i = 0;

        for (var entry : headEntries) {
            int slot = i++;

            if (slot >= inv.getContainerSize()) continue;

            ItemStack stack = entry.value().createStack();
            stack.set(DataComponents.ITEM_NAME, Component.literal(entry.getRegisteredName()));

            inv.setItem(slot, stack);
        }

        if (i > inv.getContainerSize()) {
            logger.warn("There {} eggs registered, but the debug inventory can only show {} eggs", i, inv.getContainerSize());
        }

        player.openMenu(inv);

        return 0;
    }
}
