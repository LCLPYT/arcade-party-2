package work.lclpnet.ap2.mode_default.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import work.lclpnet.ap2.mode_default.api.Skippable;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

public class SkipCommand implements KibuCommand {

    private final Skippable skippable;

    public SkipCommand(Skippable skippable) {
        this.skippable = skippable;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("skip")
                .requires(s -> s.hasPermission(2))
                .executes(this::skip);
    }

    private int skip(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (skippable.isSkip()) {
            source.sendSystemMessage(Component.literal("Already skipped"));
            return 0;
        }

        skippable.setSkip(true);
        source.sendSystemMessage(Component.literal("Skipped the preparation phase"));

        return 1;
    }
}
