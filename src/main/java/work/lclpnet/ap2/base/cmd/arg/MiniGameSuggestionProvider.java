package work.lclpnet.ap2.base.cmd.arg;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.GameInfo;

import java.util.concurrent.CompletableFuture;

public class MiniGameSuggestionProvider implements SuggestionProvider<ServerCommandSource> {

    private final MiniGameManager miniGameManager;

    public MiniGameSuggestionProvider(MiniGameManager miniGameManager) {
        this.miniGameManager = miniGameManager;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        miniGameManager.getGames().stream()
                .map(GameInfo::getId)
                .map(Identifier::toString)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }
}
