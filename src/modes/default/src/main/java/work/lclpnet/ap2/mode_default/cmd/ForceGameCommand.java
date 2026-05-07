package work.lclpnet.ap2.mode_default.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import lombok.Setter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.mode_default.cmd.arg.MiniGameSuggestionProvider;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.function.Consumer;

public class ForceGameCommand implements KibuCommand {

    private static final DynamicCommandExceptionType UNKNOWN_GAME = new DynamicCommandExceptionType(id
            -> Component.literal("Unknown game '%s'".formatted(id)));
    private final MiniGameManager miniGameManager;
    @Setter
    private Consumer<MiniGame> gameEnforcer;

    public ForceGameCommand(MiniGameManager miniGameManager, Consumer<MiniGame> gameEnforcer) {
        this.miniGameManager = miniGameManager;
        this.gameEnforcer = gameEnforcer;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("forcegame")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("gameId", IdentifierArgument.id())
                        .suggests(new MiniGameSuggestionProvider(miniGameManager))
                        .executes(this::forceGame));
    }

    private int forceGame(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier gameId = IdentifierArgument.getId(ctx, "gameId");
        MiniGame game = miniGameManager.getGame(gameId).orElseThrow(() -> UNKNOWN_GAME.create(gameId));

        gameEnforcer.accept(game);
        ctx.getSource().sendSystemMessage(Component.literal("Forcing \"%s\" as next game".formatted(gameId)));

        return 1;
    }
}
