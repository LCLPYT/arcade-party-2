package work.lclpnet.ap2.mode_default.cmd

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.type.PlayerRef.Companion.create
import work.lclpnet.ap2.mode_default.util.ScoreManager
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper

class ScoreCommand(
    private val scoreManager: ScoreManager,
    private val translations: Translations
) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(
            Commands.literal("score")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("get")
                        .executes { ctx -> getScoreSelf(ctx) }
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .executes { ctx -> getScore(ctx) }
                        )
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes { ctx -> setScoreSelf(ctx) }
                                .then(
                                    Commands.argument("targets", EntityArgument.players())
                                        .executes { ctx -> setScore(ctx) }
                                )
                        )
                )
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes { ctx -> addScoreSelf(ctx) }
                                .then(
                                    Commands.argument("targets", EntityArgument.players())
                                        .executes { ctx -> addScore(ctx) }
                                )
                        )
                )
        )
    }

    private fun addScoreSelf(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.getSource().playerOrException
        val amount = IntegerArgumentType.getInteger(ctx, "amount")

        return addScoreFor(ctx, listOf(player), amount)
    }

    private fun addScore(ctx: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(ctx, "targets")
        val amount = IntegerArgumentType.getInteger(ctx, "amount")

        return addScoreFor(ctx, players, amount)
    }

    private fun setScoreSelf(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.getSource().playerOrException
        val amount = IntegerArgumentType.getInteger(ctx, "amount")

        return setScoreFor(ctx, listOf(player), amount)
    }

    private fun setScore(ctx: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(ctx, "targets")
        val amount = IntegerArgumentType.getInteger(ctx, "amount")

        return setScoreFor(ctx, players, amount)
    }

    private fun getScoreSelf(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.getSource().playerOrException

        return getScoreFor(ctx, listOf(player))
    }

    private fun getScore(ctx: CommandContext<CommandSourceStack>): Int {
        val players = EntityArgument.getPlayers(ctx, "targets")

        return getScoreFor(ctx, players)
    }

    private fun setScoreFor(
        ctx: CommandContext<CommandSourceStack>,
        players: Collection<ServerPlayer>,
        amount: Int
    ): Int {
        for (player in players) {
            scoreManager.setScore(create(player), amount)
        }

        if (players.size == 1) {
            ctx.getSource().sendSystemMessage(
                translations.translateText(
                    ctx.getSource(), "ap2.command.score.set.single",
                    FormatWrapper.styled(players.iterator().next().scoreboardName, ChatFormatting.YELLOW),
                    FormatWrapper.styled(amount, ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN)
            )
        } else {
            ctx.getSource().sendSystemMessage(
                translations.translateText(
                    ctx.getSource(), "ap2.command.score.set.multiple",
                    FormatWrapper.styled(amount, ChatFormatting.YELLOW),
                    FormatWrapper.styled(players.size, ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN)
            )
        }

        return players.size
    }

    private fun addScoreFor(
        ctx: CommandContext<CommandSourceStack>,
        players: Collection<ServerPlayer>,
        amount: Int
    ): Int {
        for (player in players) {
            scoreManager.addScore(create(player), amount)
        }

        if (players.size == 1) {
            ctx.getSource().sendSystemMessage(
                translations.translateText(
                    ctx.getSource(), "ap2.command.score.add.single",
                    FormatWrapper.styled(amount, ChatFormatting.YELLOW),
                    FormatWrapper.styled(players.iterator().next().scoreboardName, ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN)
            )
        } else {
            ctx.getSource().sendSystemMessage(
                translations.translateText(
                    ctx.getSource(), "ap2.command.score.add.multiple",
                    FormatWrapper.styled(amount, ChatFormatting.YELLOW),
                    FormatWrapper.styled(players.size, ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN)
            )
        }

        return players.size
    }

    private fun getScoreFor(ctx: CommandContext<CommandSourceStack>, players: Collection<ServerPlayer>): Int {
        val src = ctx.getSource()

        if (players.size == 1) {
            val ref = create(players.iterator().next())
            val score = scoreManager.getScore(ref)

            src.sendSystemMessage(
                translations.translateText(
                    src, "ap2.command.score.get.single",
                    FormatWrapper.styled(ref.name, ChatFormatting.YELLOW),
                    FormatWrapper.styled(score, ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN)
            )

            return 1
        }

        src.sendSystemMessage(
            translations.translateText(src, "ap2.command.score.get.multiple_header")
                .withStyle(ChatFormatting.GREEN)
        )

        for (player in players) {
            val ref = create(player)

            src.sendSystemMessage(
                translations.translateText(
                    src, "ap2.command.score.get.row",
                    FormatWrapper.styled(ref.name, ChatFormatting.YELLOW),
                    FormatWrapper.styled(scoreManager.getScore(ref), ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN)
            )
        }

        return players.size
    }
}
