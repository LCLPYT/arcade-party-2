package work.lclpnet.ap2.mode_default.cmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.mode_default.util.ScoreManager;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;
import work.lclpnet.kibu.translate.Translations;

import java.util.Collection;
import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.util.Formatting.GREEN;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class ScoreCommand implements KibuCommand {

    private final ScoreManager scoreManager;
    private final Translations translations;

    public ScoreCommand(ScoreManager scoreManager, Translations translations) {
        this.scoreManager = scoreManager;
        this.translations = translations;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("score")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("get")
                        .executes(this::getScoreSelf)
                        .then(argument("targets", EntityArgumentType.players())
                                .executes(this::getScore)))
                .then(literal("set")
                        .then(argument("amount", integer(0))
                                .executes(this::setScoreSelf)
                                .then(argument("targets", EntityArgumentType.players())
                                        .executes(this::setScore))))
                .then(literal("add")
                        .then(argument("amount", integer(0))
                                .executes(this::addScoreSelf)
                                .then(argument("targets", EntityArgumentType.players())
                                        .executes(this::addScore)))));
    }

    private int addScoreSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        return addScoreFor(ctx, List.of(player), amount);
    }

    private int addScore(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var players = EntityArgumentType.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        return addScoreFor(ctx, players, amount);
    }

    private int setScoreSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        return setScoreFor(ctx, List.of(player), amount);
    }

    private int setScore(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var players = EntityArgumentType.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        return setScoreFor(ctx, players, amount);
    }

    private int getScoreSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        return getScoreFor(ctx, List.of(player));
    }

    private int getScore(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var players = EntityArgumentType.getPlayers(ctx, "targets");

        return getScoreFor(ctx,  players);
    }

    private int setScoreFor(CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players, int amount) {
        for (ServerPlayerEntity player : players) {
            scoreManager.setScore(PlayerRef.create(player), amount);
        }

        if (players.size() == 1) {
           ctx.getSource().sendMessage(translations.translateText(ctx.getSource(), "ap2.command.score.set.single",
                   styled(players.iterator().next().getNameForScoreboard(), YELLOW),
                   styled(amount, YELLOW)).formatted(GREEN));
        } else {
            ctx.getSource().sendMessage(translations.translateText(ctx.getSource(), "ap2.command.score.set.multiple",
                    styled(amount, YELLOW),
                    styled(players.size(), YELLOW)).formatted(GREEN));
        }

        return players.size();
    }

    private int addScoreFor(CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players, int amount) {
        for (ServerPlayerEntity player : players) {
            scoreManager.addScore(PlayerRef.create(player), amount);
        }

        if (players.size() == 1) {
            ctx.getSource().sendMessage(translations.translateText(ctx.getSource(), "ap2.command.score.add.single",
                    styled(amount, YELLOW),
                    styled(players.iterator().next().getNameForScoreboard(), YELLOW)).formatted(GREEN));
        } else {
            ctx.getSource().sendMessage(translations.translateText(ctx.getSource(), "ap2.command.score.add.multiple",
                    styled(amount, YELLOW),
                    styled(players.size(), YELLOW)).formatted(GREEN));
        }

        return players.size();
    }

    private int getScoreFor(CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players) {
        ServerCommandSource src = ctx.getSource();

        if (players.size() == 1) {
            PlayerRef ref = PlayerRef.create(players.iterator().next());
            int score = scoreManager.getScore(ref);

            src.sendMessage(translations.translateText(src, "ap2.command.score.get.single",
                    styled(ref.name(), YELLOW),
                    styled(score, YELLOW)).formatted(GREEN));

            return 1;
        }

        src.sendMessage(translations.translateText(src, "ap2.command.score.get.multiple_header").formatted(GREEN));

        for (ServerPlayerEntity player : players) {
            PlayerRef ref = PlayerRef.create(player);

            src.sendMessage(translations.translateText(src, "ap2.command.score.get.row",
                    styled(ref.name(), YELLOW),
                    styled(scoreManager.getScore(ref), YELLOW)).formatted(GREEN));
        }

        return players.size();
    }
}
