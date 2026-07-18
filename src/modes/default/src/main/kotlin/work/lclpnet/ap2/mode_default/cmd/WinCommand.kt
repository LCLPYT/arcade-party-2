package work.lclpnet.ap2.mode_default.cmd

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.MiniGameResults
import work.lclpnet.ap2.game.MiniGameResults.PlayerResult
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.data.type.PlayerRef.Companion.create
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import java.util.Map

class WinCommand(
    private val gameHandle: MiniGameHandle,
    private val miniGame: MiniGameInstance
) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(command())
    }

    private fun command() = Commands.literal("win")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .executes { ctx -> winSelf(ctx) }
        .then(
            Commands.literal("now")
                .executes { ctx -> winSelfNow(ctx) }
        )
        .then(
            Commands.argument("players", EntityArgument.players())
                .executes { ctx -> winPlayers(ctx) }
                .then(
                    Commands.literal("now")
                        .executes { ctx -> winPlayersNow(ctx) }
                )
        )

    private fun winSelf(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.getSource().playerOrException

        ctx.getSource().sendSystemMessage(Component.literal("Made yourself the winner of the current mini game"))

        miniGame.winManager.win(player)

        return 1
    }

    private fun winSelfNow(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.getSource().playerOrException

        ctx.getSource()!!.sendSystemMessage(Component.literal("Made yourself the winner of the current mini game"))

        complete(player)

        return 1
    }

    private fun winPlayers(ctx: CommandContext<CommandSourceStack>): Int {
        val players = getWinners(ctx)

        miniGame.winManager.win(players)

        return 1
    }

    private fun winPlayersNow(ctx: CommandContext<CommandSourceStack>): Int {
        val players = getWinners(ctx)

        complete(players)

        return 1
    }

    private fun complete(winner: ServerPlayer) {
        val ref = create(winner)
        val res = PlayerResult(ref, 1)

        gameHandle.complete(
            MiniGameResults(
                MiniGameResults.Status.SUCCESS,
                Map.of<PlayerRef?, PlayerResult?>(ref, res)
            )
        )
    }

    private fun complete(winners: Set<ServerPlayer>) {
        val entries = winners
            .map(PlayerRef::create)
            .associateWith { PlayerResult(it, 1) }

        gameHandle.complete(MiniGameResults(MiniGameResults.Status.SUCCESS, entries))
    }
}

private fun getWinners(ctx: CommandContext<CommandSourceStack>): Set<ServerPlayer> {
    val players = EntityArgument.getPlayers(ctx, "players").toSet()

    val count = players.size

    val source = ctx.getSource()

    if (count == 1) {
        val winner = players.iterator().next()

        source.sendSystemMessage(
            Component.literal("Made ${winner.scoreboardName} the winner of the current mini game")
        )
    } else {
        val names = players.joinToString { it.scoreboardName }

        source.sendSystemMessage(
            Component.literal("Made $names the winners of the current mini game")
        )
    }

    return players
}
