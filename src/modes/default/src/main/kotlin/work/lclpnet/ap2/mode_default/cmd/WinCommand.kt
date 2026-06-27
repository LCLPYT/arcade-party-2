package work.lclpnet.ap2.mode_default.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.MiniGameInstance;
import work.lclpnet.ap2.game.data.type.PlayerRef;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("win")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(this::winSelf)
                .then(literal("now")
                        .executes(this::winSelfNow))
                .then(argument("players", EntityArgument.players())
                        .executes(this::winPlayers)
                        .then(literal("now")
                                .executes(this::winPlayersNow)));
    }

    private int winSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ctx.getSource().sendSystemMessage(Component.literal("Made yourself the winner of the current mini game"));

        miniGame.getWinManager().win(player);

        return 1;
    }

    private int winSelfNow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ctx.getSource().sendSystemMessage(Component.literal("Made yourself the winner of the current mini game"));

        complete(player);

        return 1;
    }

    private int winPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var players = getWinners(ctx);

        miniGame.getWinManager().win(players);

        return 1;
    }

    private int winPlayersNow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var players = getWinners(ctx);

        complete(players);

        return 1;
    }

    private void complete(ServerPlayer winner) {
        PlayerRef ref = PlayerRef.create(winner);
        var res = new MiniGameResults.PlayerResult(ref, 1);

        gameHandle.complete(new MiniGameResults(MiniGameResults.Status.SUCCESS, Map.of(ref, res)));
    }

    private void complete(Set<ServerPlayer> winners) {
        var entries = winners.stream()
                .map(PlayerRef::create)
                .collect(Collectors.toMap(Function.identity(), ref -> new MiniGameResults.PlayerResult(ref, 1)));

        gameHandle.complete(new MiniGameResults(MiniGameResults.Status.SUCCESS, entries));
    }

    @NotNull
    private static Set<ServerPlayer> getWinners(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var players = new HashSet<>(EntityArgument.getPlayers(ctx, "players"));

        int count = players.size();

        CommandSourceStack source = ctx.getSource();

        if (count == 1) {
            ServerPlayer winner = players.iterator().next();
            source.sendSystemMessage(Component.literal("Made %s the winner of the current mini game".formatted(winner.getScoreboardName())));
        } else {
            String names = players.stream()
                    .map(Player::getScoreboardName)
                    .collect(Collectors.joining(", "));

            source.sendSystemMessage(Component.literal("Made %s the winners of the current mini game".formatted(names)));
        }
        return players;
    }
}
