package work.lclpnet.ap2.game.guess_it.util;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import static net.minecraft.server.command.CommandManager.literal;

public class SkipChallengeCommand implements KibuCommand {

    private final Runnable skip;

    public SkipChallengeCommand(Runnable skip) {
        this.skip = skip;
    }

    @Override
    public void register(CommandRegistrar commands) {
        commands.registerCommand(literal("ap2:skip_challenge")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(this::skipChallenge));
    }

    private int skipChallenge(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendMessage(Text.literal("Skipped the current challenge"));

        skip.run();

        return 1;
    }
}
