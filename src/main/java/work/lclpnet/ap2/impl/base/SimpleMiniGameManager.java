package work.lclpnet.ap2.impl.base;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.game.MiniGames;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link MiniGameManager} that retrieves games from {@link MiniGames#registerGames(Set)}.
 */
public class SimpleMiniGameManager implements MiniGameManager {

    private final BiMap<Identifier, MiniGame> games;

    public SimpleMiniGameManager(Logger logger) {
        Set<MiniGame> registry = new HashSet<>();

        MiniGames.registerGames(registry);

        BiMap<Identifier, MiniGame> byId = HashBiMap.create();

        for (MiniGame miniGame : registry) {
            Identifier id = miniGame.getId();

            if (byId.put(id, miniGame) != null) {
                logger.warn("Mini game id collision with id {}", id);
            }
        }

        this.games = ImmutableBiMap.copyOf(byId);
    }

    @Override
    public Set<MiniGame> getGames() {
        return games.values();
    }

    @Override
    public Optional<MiniGame> getGame(Identifier gameId) {
        return Optional.ofNullable(games.get(gameId));
    }
}
