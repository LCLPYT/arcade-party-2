package work.lclpnet.ap2.game.guess_it.util;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import static net.minecraft.commands.Commands.literal;

public class SkipChallengeCommand implements KibuCommand {

    private final Runnable skip;

    public SkipChallengeCommand(Runnable skip) {
        this.skip = skip;
    }

    @Override
    public void register(CommandRegistrar commands) {
        commands.registerCommand(literal("ap2:skip_challenge")
                .requires(s -> s.hasPermission(2))
                .executes(this::skipChallenge));
    }

    private int skipChallenge(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(Component.literal("Skipped the current challenge"));

        skip.run();

        return 1;
    }
}
