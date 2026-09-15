package work.lclpnet.ap2.mode_default.cmd

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.player.PlayerManager
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import work.lclpnet.kibu.translate.Translations

class SpectatorCommand(
    private val playerManager: PlayerManager,
    private val translations: Translations,
    private val immediate: Boolean = false,
    private val onToggle: (ServerPlayer) -> Unit = {}
) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(command())
    }

    private fun command() = Commands.literal("spectator")
        .executes { ctx -> toggle(ctx) }

    private fun toggle(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException

        val key = if (playerManager.isPermanentSpectator(player)) {
            playerManager.removePermanentSpectator(player)
            if (immediate) "ap2.spectator.disabled" else "ap2.spectator.disabled_delayed"
        } else {
            playerManager.addPermanentSpectator(player)
            if (immediate) "ap2.spectator.enabled" else "ap2.spectator.enabled_delayed"
        }

        translations.translateText(key).sendTo(player)

        onToggle(player)

        return 1
    }
}
