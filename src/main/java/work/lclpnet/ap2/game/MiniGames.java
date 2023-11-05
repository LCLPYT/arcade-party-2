package work.lclpnet.ap2.game;

import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.game.bow_spleef.BowSpleefMiniGame;
import work.lclpnet.ap2.game.fine_tuning.FineTuningMiniGame;
import work.lclpnet.ap2.game.mirror_hop.MirrorHopMiniGame;
import work.lclpnet.ap2.game.spleef.SpleefMiniGame;

import java.util.Set;

public class MiniGames {

    /**
     * Registers all available {@link MiniGame} types.
     * @param games The game set.
     */
    public static void registerGames(Set<MiniGame> games) {
        games.add(new SpleefMiniGame());
        games.add(new BowSpleefMiniGame());
        games.add(new MirrorHopMiniGame());
        games.add(new FineTuningMiniGame());
    }
}
