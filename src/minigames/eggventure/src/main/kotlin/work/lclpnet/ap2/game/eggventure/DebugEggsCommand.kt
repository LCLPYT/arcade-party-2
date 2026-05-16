package work.lclpnet.ap2.game.eggventure

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import org.slf4j.Logger
import work.lclpnet.ap2.impl.tags.PlayerHeadTags
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import work.lclpnet.kibu.inv.type.KibuInventory

class DebugEggsCommand(private val logger: Logger) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(
            Commands.literal("ap2:eggs")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(::showEggsInventory)
        )
    }

    private fun showEggsInventory(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException

        val inv = KibuInventory(6, Component.literal("Eggs"))

        val headEntries = player.level().registryAccess()
            .lookupOrThrow(ApRegistries.PLAYER_HEAD)
            .getTagOrEmpty(PlayerHeadTags.EASTER_EGGS)

        var i = 0

        for (entry in headEntries) {
            val slot = i++

            if (slot >= inv.containerSize) continue

            val stack = entry.value().createStack()
            stack.set(DataComponents.ITEM_NAME, Component.literal(entry.registeredName))

            inv.setItem(slot, stack)
        }

        if (i > inv.containerSize) {
            logger.warn("There {} eggs registered, but the debug inventory can only show {} eggs", i, inv.containerSize)
        }

        player.openMenu(inv)

        return 0
    }
}
