package work.lclpnet.ap2.game.dance_floor.cmd

import kotlinx.coroutines.Runnable
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class SkipSongCommand(val skipCurrent: Runnable) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(
            Commands.literal("ap2:skip")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes { ctx ->
                ctx.source.sendSystemMessage(Component.literal("Skipped the current song"))
                skipCurrent.run()
                1
            })
    }
}