package work.lclpnet.ap2.impl.base;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.MiniGame;

import java.util.*;

/**
 * A {@link MiniGameManager} that retrieves games from Fabric entry points of type "ap2:minigame".
 */
public class FabricMiniGameManager implements MiniGameManager {

    public static final String MINIGAME_ENTRYPOINT = "ap2:minigame";

    private final BiMap<Identifier, MiniGame> games;
    private final Set<MiniGame> gameSet;  // use a separate set that is backed by a LinkedHashSet (order preserving)

    public FabricMiniGameManager(Logger logger) {
        List<MiniGame> miniGames = FabricLoader.getInstance()
                .getEntrypointContainers(MINIGAME_ENTRYPOINT, MiniGame.class)
                .stream()
                .sorted(minigameOrdering())
                .map(EntrypointContainer::getEntrypoint)
                .toList();

        Set<MiniGame> registry = new LinkedHashSet<>(miniGames);

        BiMap<Identifier, MiniGame> byId = HashBiMap.create();

        for (MiniGame miniGame : registry) {
            Identifier id = miniGame.getId();

            if (byId.put(id, miniGame) != null) {
                logger.warn("Mini game id collision with id {}", id);
            }
        }

        this.games = ImmutableBiMap.copyOf(byId);
        this.gameSet = Collections.unmodifiableSet(registry);
    }

    private Comparator<EntrypointContainer<MiniGame>> minigameOrdering() {
        return Comparator.comparingLong(container -> {
            CustomValue timestamp = container.getProvider().getMetadata().getCustomValue("timestamp");

            return timestamp != null ? timestamp.getAsNumber().longValue() : Long.MAX_VALUE;
        });
    }

    @Override
    public Set<MiniGame> getGames() {
        return gameSet;
    }

    @Override
    public Optional<MiniGame> getGame(Identifier gameId) {
        return Optional.ofNullable(games.get(gameId));
    }
}
