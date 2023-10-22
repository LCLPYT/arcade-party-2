package work.lclpnet.ap2.api.base;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGame;

import java.util.Optional;
import java.util.Set;

public interface MiniGameManager {

    Set<MiniGame> getGames();

    Optional<MiniGame> getGame(Identifier gameId);
}
