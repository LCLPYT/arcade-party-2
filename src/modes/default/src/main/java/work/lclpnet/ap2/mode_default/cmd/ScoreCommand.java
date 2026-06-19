package work.lclpnet.ap2.mode_default.cmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.ap2.game.data.type.PlayerRef;
import work.lclpnet.ap2.mode_default.util.ScoreManager;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;
import work.lclpnet.kibu.translate.Translations;

import java.util.Collection;
import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.ChatFormatting.GREEN;
import static net.minecraft.ChatFormatting.YELLOW;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
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
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("get")
                        .executes(this::getScoreSelf)
                        .then(argument("targets", EntityArgument.players())
                                .executes(this::getScore)))
                .then(literal("set")
                        .then(argument("amount", integer(0))
                                .executes(this::setScoreSelf)
                                .then(argument("targets", EntityArgument.players())
                                        .executes(this::setScore))))
                .then(literal("add")
                        .then(argument("amount", integer(0))
                                .executes(this::addScoreSelf)
                                .then(argument("targets", EntityArgument.players())
                                        .executes(this::addScore)))));
    }

    private int addScoreSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        return addScoreFor(ctx, List.of(player), amount);
    }

    private int addScore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        return addScoreFor(ctx, players, amount);
    }

    private int setScoreSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        return setScoreFor(ctx, List.of(player), amount);
    }

    private int setScore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        return setScoreFor(ctx, players, amount);
    }

    private int getScoreSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        return getScoreFor(ctx, List.of(player));
    }

    private int getScore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(ctx, "targets");

        return getScoreFor(ctx,  players);
    }

    private int setScoreFor(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, int amount) {
        for (ServerPlayer player : players) {
            scoreManager.setScore(PlayerRef.create(player), amount);
        }

        if (players.size() == 1) {
           ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(), "ap2.command.score.set.single",
                   styled(players.iterator().next().getScoreboardName(), YELLOW),
                   styled(amount, YELLOW)).withStyle(GREEN));
        } else {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(), "ap2.command.score.set.multiple",
                    styled(amount, YELLOW),
                    styled(players.size(), YELLOW)).withStyle(GREEN));
        }

        return players.size();
    }

    private int addScoreFor(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, int amount) {
        for (ServerPlayer player : players) {
            scoreManager.addScore(PlayerRef.create(player), amount);
        }

        if (players.size() == 1) {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(), "ap2.command.score.add.single",
                    styled(amount, YELLOW),
                    styled(players.iterator().next().getScoreboardName(), YELLOW)).withStyle(GREEN));
        } else {
            ctx.getSource().sendSystemMessage(translations.translateText(ctx.getSource(), "ap2.command.score.add.multiple",
                    styled(amount, YELLOW),
                    styled(players.size(), YELLOW)).withStyle(GREEN));
        }

        return players.size();
    }

    private int getScoreFor(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        CommandSourceStack src = ctx.getSource();

        if (players.size() == 1) {
            PlayerRef ref = PlayerRef.create(players.iterator().next());
            int score = scoreManager.getScore(ref);

            src.sendSystemMessage(translations.translateText(src, "ap2.command.score.get.single",
                    styled(ref.name(), YELLOW),
                    styled(score, YELLOW)).withStyle(GREEN));

            return 1;
        }

        src.sendSystemMessage(translations.translateText(src, "ap2.command.score.get.multiple_header").withStyle(GREEN));

        for (ServerPlayer player : players) {
            PlayerRef ref = PlayerRef.create(player);

            src.sendSystemMessage(translations.translateText(src, "ap2.command.score.get.row",
                    styled(ref.name(), YELLOW),
                    styled(scoreManager.getScore(ref), YELLOW)).withStyle(GREEN));
        }

        return players.size();
    }
}
