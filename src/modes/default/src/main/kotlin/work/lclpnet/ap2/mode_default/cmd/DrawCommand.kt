package work.lclpnet.ap2.mode_default.cmd

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.MiniGameResults
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class DrawCommand(
    private val gameHandle: MiniGameHandle,
    private val miniGame: MiniGameInstance
) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(command())
    }

    private fun command() = Commands.literal("draw")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .executes { ctx -> draw(ctx) }
        .then(
            Commands.literal("now")
                .executes { ctx -> drawNow(ctx) }
        )

    private fun draw(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.getSource().sendSystemMessage(Component.literal("Ended the current mini game with a draw"))

        dispatchDraw(miniGame)

        return 1
    }

    private fun drawNow(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.getSource().sendSystemMessage(Component.literal("Ended the current mini game with a draw"))

        gameHandle.complete(MiniGameResults.EMPTY)

        return 1
    }

    companion object {
        fun dispatchDraw(instance: MiniGameInstance) {
            instance.winManager.draw()
        }
    }
}
