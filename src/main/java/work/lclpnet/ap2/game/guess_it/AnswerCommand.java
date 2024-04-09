package work.lclpnet.ap2.game.guess_it;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.guess_it.data.InputManager;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.cmd.KibuCommand;

import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AnswerCommand implements KibuCommand {

    private final Participants participants;
    private final InputManager inputManager;

    public AnswerCommand(Participants participants, InputManager inputManager) {
        this.participants = participants;
        this.inputManager = inputManager;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return literal("answer")
                .requires(s -> {
                    ServerPlayerEntity player = s.getPlayer();
                    return player != null && participants.isParticipating(player);
                })
                .then(argument("input", string())
                        .executes(this::doAnswer));
    }

    private int doAnswer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String input = StringArgumentType.getString(ctx, "input");

        inputManager.input(player, input);

        return 1;
    }
}
