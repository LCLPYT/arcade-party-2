package work.lclpnet.ap2.game.guess_it.util;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.guess_it.data.Challenge;
import work.lclpnet.ap2.game.guess_it.data.GuessItManager;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import static net.minecraft.server.command.CommandManager.literal;

public class SetChallengeCommand implements KibuCommand {

    private final GuessItManager manager;
    private final Runnable skip;

    public SetChallengeCommand(GuessItManager manager, Runnable skip) {
        this.manager = manager;
        this.skip = skip;
    }

    @Override
    public void register(CommandRegistrar commands) {
        var root = literal("ap2:set_challenge")
                .requires(s -> s.hasPermissionLevel(2));

        for (Challenge challenge : manager.getChallenges()) {
            var node = literal(challenge.id())
                    .executes(ctx -> setChallenge(ctx, challenge, null));

            Challenge.Initializer initializer = (ctx, init) -> {
                setChallenge(ctx, challenge, init);
            };

            challenge.provideInitCommand(node, initializer);

            root.then(node);
        }

        commands.registerCommand(root);
    }

    private int setChallenge(CommandContext<ServerCommandSource> ctx, Challenge challenge, @Nullable Object init) {
        ctx.getSource().sendMessage(Text.literal("Set challenge to \"%s\"".formatted(challenge.id())));

        manager.pushChallenge(challenge, init);
        skip.run();

        return 1;
    }
}
