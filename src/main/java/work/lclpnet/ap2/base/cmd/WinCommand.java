package work.lclpnet.ap2.base.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.api.game.WinManagerAccess;
import work.lclpnet.ap2.api.game.WinManagerView;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.cmd.KibuCommand;

import java.util.HashSet;
import java.util.stream.Collectors;

public class WinCommand implements KibuCommand {

    private final MiniGameHandle gameHandle;
    private final MiniGameInstance miniGame;

    public WinCommand(MiniGameHandle gameHandle, MiniGameInstance miniGame) {
        this.gameHandle = gameHandle;
        this.miniGame = miniGame;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return CommandManager.literal("win")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(this::winSelf)
                .then(CommandManager.argument("players", EntityArgumentType.players())
                        .executes(this::winPlayers));
    }

    private int winSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        ctx.getSource().sendMessage(Text.literal("Made yourself the winner of the current mini game"));

        if (miniGame instanceof WinManagerView view) {
            WinManagerAccess winManagerAccess = view.getWinManagerAccess();
            winManagerAccess.win(player);
        } else {
            gameHandle.complete(player);
        }

        return 1;
    }

    private int winPlayers(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var players = new HashSet<>(EntityArgumentType.getPlayers(ctx, "players"));

        int count = players.size();

        ServerCommandSource source = ctx.getSource();

        if (count == 1) {
            ServerPlayerEntity winner = players.iterator().next();
            source.sendMessage(Text.literal("Made %s the winner of the current mini game".formatted(winner.getNameForScoreboard())));
        } else {
            String names = players.stream()
                    .map(PlayerEntity::getNameForScoreboard)
                    .collect(Collectors.joining(", "));

            source.sendMessage(Text.literal("Made %s the winners of the current mini game".formatted(names)));
        }

        if (miniGame instanceof WinManagerView view) {
            WinManagerAccess winManagerAccess = view.getWinManagerAccess();
            winManagerAccess.win(players);
        } else {
            gameHandle.complete(players);
        }

        return 1;
    }
}
