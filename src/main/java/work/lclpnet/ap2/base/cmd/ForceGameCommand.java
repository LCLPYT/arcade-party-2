package work.lclpnet.ap2.base.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.base.cmd.arg.MiniGameSuggestionProvider;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.cmd.KibuCommand;

import java.util.function.Consumer;

public class ForceGameCommand implements KibuCommand {

    private static final DynamicCommandExceptionType UNKNOWN_GAME = new DynamicCommandExceptionType(id
            -> Text.literal("Unknown game '%s'".formatted(id)));
    private final MiniGameManager miniGameManager;
    private final Consumer<MiniGame> gameEnforcer;

    public ForceGameCommand(MiniGameManager miniGameManager, Consumer<MiniGame> gameEnforcer) {
        this.miniGameManager = miniGameManager;
        this.gameEnforcer = gameEnforcer;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return CommandManager.literal("forcegame")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.argument("gameId", IdentifierArgumentType.identifier())
                        .suggests(new MiniGameSuggestionProvider(miniGameManager))
                        .executes(this::forceGame));
    }

    private int forceGame(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Identifier gameId = IdentifierArgumentType.getIdentifier(ctx, "gameId");
        MiniGame game = miniGameManager.getGame(gameId).orElseThrow(() -> UNKNOWN_GAME.create(gameId));

        gameEnforcer.accept(game);
        ctx.getSource().sendMessage(Text.literal("Forced mini-game \"%s\"".formatted(gameId)));

        return 1;
    }
}
