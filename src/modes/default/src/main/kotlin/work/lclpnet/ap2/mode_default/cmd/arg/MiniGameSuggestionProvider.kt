package work.lclpnet.ap2.mode_default.cmd.arg

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import work.lclpnet.ap2.impl.base.MiniGameManager
import java.util.concurrent.CompletableFuture

class MiniGameSuggestionProvider(
    private val miniGameManager: MiniGameManager
) : SuggestionProvider<CommandSourceStack> {

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        for (game in miniGameManager.games) {
            builder.suggest(game.id.toString())
        }

        return builder.buildFuture()
    }
}
