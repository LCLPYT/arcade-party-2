package work.lclpnet.ap2.game.dance_floor.cmd

import kotlinx.coroutines.Runnable
import net.minecraft.server.command.CommandManager
import net.minecraft.text.Text
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class SkipSongCommand(val skipCurrent: Runnable) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(CommandManager.literal("ap2:skip")
            .requires { s -> s.hasPermissionLevel(2) }
            .executes { ctx ->
                ctx.source.sendMessage(Text.literal("Skipped the current song"))
                skipCurrent.run()
                1
            })
    }
}