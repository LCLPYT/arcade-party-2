package work.lclpnet.ap2.base.cmd.arg;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.map.MapFacade;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class MapSuggestionProvider implements SuggestionProvider<ServerCommandSource> {

    private final MapFacade mapFacade;
    private final Supplier<Optional<MiniGame>> gameSupplier;

    public MapSuggestionProvider(MapFacade mapFacade, Supplier<Optional<MiniGame>> gameSupplier) {
        this.mapFacade = mapFacade;
        this.gameSupplier = gameSupplier;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return gameSupplier.get()
                .map(buildSuggestions(builder))
                .orElseGet(builder::buildFuture);
    }

    private Function<MiniGame, CompletableFuture<Suggestions>> buildSuggestions(SuggestionsBuilder builder) {
        return miniGame -> {
            mapFacade.getMapIds(miniGame.getId()).stream()
                    .map(Identifier::toString)
                    .forEach(builder::suggest);

            return builder.buildFuture();
        };
    }
}
