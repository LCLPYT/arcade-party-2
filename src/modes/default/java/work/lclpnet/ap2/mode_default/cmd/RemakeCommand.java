package work.lclpnet.ap2.mode_default.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraft.commands.Commands.literal;

public class RemakeCommand implements KibuCommand {

    private final MiniGameHandle handle;
    private final AtomicBoolean remake;

    public RemakeCommand(MiniGameHandle handle, AtomicBoolean remake) {
        this.handle = handle;
        this.remake = remake;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("remake")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(this::remake);
    }

    private int remake(CommandContext<CommandSourceStack> ctx) {
        if (remake.getAndSet(true)) {
            return 0;
        }

        ctx.getSource().sendSystemMessage(Component.literal("Restarting the current mini game..."));

        handle.complete(MiniGameResults.EMPTY);

        return 1;
    }
}
