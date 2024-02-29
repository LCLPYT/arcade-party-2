package work.lclpnet.ap2.game;

import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.game.anvil_fall.AnvilFallMiniGame;
import work.lclpnet.ap2.game.block_dissolve.BlockDissolveMiniGame;
import work.lclpnet.ap2.game.bow_spleef.BowSpleefMiniGame;
import work.lclpnet.ap2.game.cozy_campfire.CozyCampfireMiniGame;
import work.lclpnet.ap2.game.fine_tuning.FineTuningMiniGame;
import work.lclpnet.ap2.game.hot_potato.HotPotatoMiniGame;
import work.lclpnet.ap2.game.jump_and_run.JumpAndRunMiniGame;
import work.lclpnet.ap2.game.mining_battle.MiningBattleMiniGame;
import work.lclpnet.ap2.game.mirror_hop.MirrorHopMiniGame;
import work.lclpnet.ap2.game.one_in_the_chamber.OneInTheChamberMiniGame;
import work.lclpnet.ap2.game.panda_finder.PandaFinderMiniGame;
import work.lclpnet.ap2.game.spleef.SpleefMiniGame;
import work.lclpnet.ap2.game.tnt_run.TntRunMiniGame;
import work.lclpnet.ap2.game.treasure_hunter.TreasureHunterMinigame;

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
        games.add(new TreasureHunterMinigame());
        games.add(new OneInTheChamberMiniGame());
        games.add(new AnvilFallMiniGame());
        games.add(new PandaFinderMiniGame());
        games.add(new JumpAndRunMiniGame());
        games.add(new TntRunMiniGame());
        games.add(new HotPotatoMiniGame());
        games.add(new BlockDissolveMiniGame());
        games.add(new CozyCampfireMiniGame());
        games.add(new MiningBattleMiniGame());
    }
}
