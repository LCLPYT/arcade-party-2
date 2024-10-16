package work.lclpnet.ap2.game;

import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.game.aim_master.AimMasterMiniGame;
import work.lclpnet.ap2.game.anvil_fall.AnvilFallMiniGame;
import work.lclpnet.ap2.game.apocalypse_survival.ApocalypseSurvivalMiniGame;
import work.lclpnet.ap2.game.block_dissolve.BlockDissolveMiniGame;
import work.lclpnet.ap2.game.bow_spleef.BowSpleefMiniGame;
import work.lclpnet.ap2.game.chicken_shooter.ChickenShooterMiniGame;
import work.lclpnet.ap2.game.cozy_campfire.CozyCampfireMiniGame;
import work.lclpnet.ap2.game.fine_tuning.FineTuningMiniGame;
import work.lclpnet.ap2.game.glowing_bomb.GlowingBombMiniGame;
import work.lclpnet.ap2.game.guess_it.GuessItMiniGame;
import work.lclpnet.ap2.game.hot_potato.HotPotatoMiniGame;
import work.lclpnet.ap2.game.jump_and_run.JumpAndRunMiniGame;
import work.lclpnet.ap2.game.knockout.KnockoutMiniGame;
import work.lclpnet.ap2.game.maniac_digger.ManiacDiggerMiniGame;
import work.lclpnet.ap2.game.mimicry.MimicryMiniGame;
import work.lclpnet.ap2.game.mining_battle.MiningBattleMiniGame;
import work.lclpnet.ap2.game.mirror_hop.MirrorHopMiniGame;
import work.lclpnet.ap2.game.musical_minecart.MusicalMinecartMiniGame;
import work.lclpnet.ap2.game.one_in_the_chamber.OneInTheChamberMiniGame;
import work.lclpnet.ap2.game.panda_finder.PandaFinderMiniGame;
import work.lclpnet.ap2.game.pig_race.PigRaceMiniGame;
import work.lclpnet.ap2.game.pillar_battle.PillarBattleMiniGame;
import work.lclpnet.ap2.game.red_light_green_light.RedLightGreenLightMiniGame;
import work.lclpnet.ap2.game.snowball_fight.SnowballFightMiniGame;
import work.lclpnet.ap2.game.speed_builders.SpeedBuildersMiniGame;
import work.lclpnet.ap2.game.splashy_dropper.SplashyDropperMiniGame;
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
        games.add(new KnockoutMiniGame());
        games.add(new MusicalMinecartMiniGame());
        games.add(new GuessItMiniGame());
        games.add(new PigRaceMiniGame());
        games.add(new RedLightGreenLightMiniGame());
        games.add(new SnowballFightMiniGame());
        games.add(new ChickenShooterMiniGame());
        games.add(new SpeedBuildersMiniGame());
        games.add(new MimicryMiniGame());
        games.add(new SplashyDropperMiniGame());
        games.add(new GlowingBombMiniGame());
        games.add(new ManiacDiggerMiniGame());
        games.add(new ApocalypseSurvivalMiniGame());
        games.add(new PillarBattleMiniGame());
        games.add(new AimMasterMiniGame());
    }
}
