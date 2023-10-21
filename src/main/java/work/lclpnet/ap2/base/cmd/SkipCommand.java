package work.lclpnet.ap2.base.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import work.lclpnet.ap2.base.api.Skippable;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.cmd.KibuCommand;

public class SkipCommand implements KibuCommand {

    private final Skippable skippable;

    public SkipCommand(Skippable skippable) {
        this.skippable = skippable;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return CommandManager.literal("skip")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(this::skip);
    }

    private int skip(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        if (skippable.isSkip()) {
            source.sendMessage(Text.literal("Already skipped"));
            return 0;
        }

        skippable.setSkip(true);
        source.sendMessage(Text.literal("Skipped the preparation phase"));

        return 1;
    }
}
