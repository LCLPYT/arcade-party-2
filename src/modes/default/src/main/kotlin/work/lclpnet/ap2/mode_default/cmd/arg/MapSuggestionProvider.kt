package work.lclpnet.ap2.mode_default.cmd.arg

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
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

        return CoroutineScope(Dispatchers.Default).future {
            for (identifier in mapFacade.getMapIds(miniGame.id)) {
                builder.suggest(identifier.toString())
            }

            builder.build()
        }
    }
}
