package work.lclpnet.ap2.api.base;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.game.MiniGames;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public interface MiniGameManager {

    Set<MiniGame> getGames();

    Optional<MiniGame> getGame(Identifier gameId);

    static Set<MiniGame> getAllGames() {
        HashSet<MiniGame> games = new HashSet<>();
        MiniGames.registerGames(games);

        return games;
    }
}
