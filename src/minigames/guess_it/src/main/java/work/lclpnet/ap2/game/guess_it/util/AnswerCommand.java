package work.lclpnet.ap2.game.guess_it.util;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.guess_it.data.InputManager;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("answer")
                .requires(s -> {
                    ServerPlayer player = s.getPlayer();
                    return player != null && participants.isParticipating(player);
                })
                .then(argument("input", string())
                        .executes(this::doAnswer));
    }

    private int doAnswer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String input = StringArgumentType.getString(ctx, "input");

        inputManager.input(player, input);

        return 1;
    }
}
