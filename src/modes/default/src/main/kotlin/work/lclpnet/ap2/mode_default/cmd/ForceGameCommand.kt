package work.lclpnet.ap2.mode_default.cmd

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.impl.base.MiniGameManager
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.mode_default.cmd.arg.MiniGameSuggestionProvider
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import java.util.function.Consumer

private val UNKNOWN_GAME = DynamicCommandExceptionType { id -> Component.literal("Unknown game '$id'") }

class ForceGameCommand(
    private val miniGameManager: MiniGameManager,
    var gameEnforcer: Consumer<MiniGame>
) : KibuCommand {
    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(command())
    }

    private fun command() = Commands.literal("forcegame")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(
            Commands.argument("gameId", IdentifierArgument.id())
                .suggests(MiniGameSuggestionProvider(miniGameManager))
                .executes { ctx -> forceGame(ctx) }
        )

    private fun forceGame(ctx: CommandContext<CommandSourceStack>): Int {
        val gameId = IdentifierArgument.getId(ctx, "gameId")

        val game = miniGameManager.getGame(gameId) ?: throw UNKNOWN_GAME.create(gameId)

        gameEnforcer.accept(game)
        ctx.getSource().sendSystemMessage(Component.literal("Forcing \"$gameId\" as next game"))

        return 1
    }
}
