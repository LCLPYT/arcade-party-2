package work.lclpnet.ap2.mode_default.cmd.arg

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.impl.map.MapFacade
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class MapSuggestionProvider(
    private val mapFacade: MapFacade,
    private val gameSupplier: Supplier<MiniGame?>
) : SuggestionProvider<CommandSourceStack> {

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val miniGame = gameSupplier.get() ?: return builder.buildFuture()

        return mapFacade.getMapIds(miniGame.id)
            .thenApply { mapIds ->
                for (identifier in mapIds) {
                    builder.suggest(identifier.toString())
                }

                builder.build()
            }
    }
}
