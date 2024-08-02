package work.lclpnet.ap2.base.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraft.server.command.CommandManager.literal;

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

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return literal("remake")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(this::remake);
    }

    private int remake(CommandContext<ServerCommandSource> ctx) {
        if (remake.getAndSet(true)) {
            return 0;
        }

        ctx.getSource().sendMessage(Text.literal("Restarting the current mini game..."));

        handle.completeWithoutWinner();

        return 1;
    }
}
