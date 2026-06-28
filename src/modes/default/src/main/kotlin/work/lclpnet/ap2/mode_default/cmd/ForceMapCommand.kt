package work.lclpnet.ap2.mode_default.cmd

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.api.map.MapFacade
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.mode_default.cmd.arg.MapSuggestionProvider
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import java.util.function.Supplier

class ForceMapCommand(
    private val mapFacade: MapFacade,
    private val gameSupplier: Supplier<MiniGame?>
) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(command())
    }

    private fun command() = Commands.literal("forcemap")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(
            Commands.argument("mapId", IdentifierArgument.id())
                .suggests(MapSuggestionProvider(mapFacade, gameSupplier))
                .executes { ctx -> forceMap(ctx) }
        )

    private fun forceMap(ctx: CommandContext<CommandSourceStack>): Int {
        val mapId = IdentifierArgument.getId(ctx, "mapId")

        mapFacade.forceMap(mapId)

        ctx.getSource().sendSystemMessage(Component.literal("Next map will be \"$mapId\""))

        return 1
    }
}
