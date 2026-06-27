package work.lclpnet.ap2.mode_default.cmd.arg;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.game.MiniGame;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class MapSuggestionProvider implements SuggestionProvider<CommandSourceStack> {

    private final MapFacade mapFacade;
    private final Supplier<Optional<MiniGame>> gameSupplier;

    public MapSuggestionProvider(MapFacade mapFacade, Supplier<Optional<MiniGame>> gameSupplier) {
        this.mapFacade = mapFacade;
        this.gameSupplier = gameSupplier;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return gameSupplier.get()
                .map(buildSuggestions(builder))
                .orElseGet(builder::buildFuture);
    }

    private Function<MiniGame, CompletableFuture<Suggestions>> buildSuggestions(SuggestionsBuilder builder) {
        return miniGame -> mapFacade.getMapIds(miniGame.getId()).thenApply(mapIds -> {
            mapIds.stream().map(Identifier::toString).forEach(builder::suggest);

            return builder.build();
        });
    }
}
