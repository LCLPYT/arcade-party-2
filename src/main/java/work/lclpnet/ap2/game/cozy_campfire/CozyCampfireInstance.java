package work.lclpnet.ap2.game.cozy_campfire;

import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.game.cozy_campfire.setup.*;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.PlayerMovementObserver;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.Set;

public class CozyCampfireInstance extends TeamEliminationGameInstance {

    private static final float DAY_TIME_CHANCE = 0.25f, CLEAR_WEATHER_CHANCE = 0.6f, THUNDER_CHANCE = 0.05f;
    public static final TeamKey TEAM_RED = ApTeamKeys.RED, TEAM_BLUE = ApTeamKeys.BLUE;
    private final Random random = new Random();
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver;
    private CozyCampfireHooks hookSetup;

    public CozyCampfireInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useOldCombat();
        useSurvivalMode();

        movementObserver = new PlayerMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);
    }

    @Override
    protected void bootstrapWorld(ServerWorld world, GameMap map) {
        setupGameRules(world);
        randomizeWorldConditions(world);
    }

    @Override
    protected void prepare() {
        Participants participants = gameHandle.getParticipants();

        TeamManager teamManager = getTeamManager();
        teamManager.partitionIntoTeams(participants, Set.of(TEAM_RED, TEAM_BLUE));

        teamManager.getMinecraftTeams().forEach(team -> {
            team.setFriendlyFireAllowed(false);
            team.setShowFriendlyInvisibles(true);
            team.setCollisionRule(AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS);
        });

        CozyCampfireBaseManager baseManager = readMapData(teamManager);

        CozyCampfireFuel fuel = new CozyCampfireFuel(getWorld(), baseManager);
        fuel.registerFuel();

        teleportTeamsToSpawns();

        CozyCampfireKitManager kitManager = new CozyCampfireKitManager(teamManager, getWorld(), random);

        for (ServerPlayerEntity player : participants) {
            kitManager.giveItems(player);
        }

        TranslationService translations = gameHandle.getTranslations();
        var args = new CozyCampfireHooks.Args(fuel, baseManager, kitManager);

        hookSetup = new CozyCampfireHooks(participants, teamManager, this, translations, args);
        hookSetup.register(gameHandle.getHookRegistrar());

        setupMovementObserver(hookSetup);
    }

    @Override
    protected void ready() {
        gameHandle.protect(hookSetup::configure);

        gameHandle.getGameScheduler().interval(this::tick, 1);
    }

    private void setupMovementObserver(CozyCampfireHooks hookSetup) {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        MinecraftServer server = gameHandle.getServer();

        movementObserver.init(hooks, server);

        hookSetup.configureBaseRegionEvents(collisionDetector, movementObserver);
    }

    private CozyCampfireBaseManager readMapData(TeamManager teamManager) {
        CozyCampfireReader setup = new CozyCampfireReader(getMap(), gameHandle.getLogger());
        var bases = setup.readBases(teamManager.getTeams());

        return new CozyCampfireBaseManager(bases, teamManager);
    }

    private void setupGameRules(ServerWorld world) {
        MinecraftServer server = gameHandle.getServer();

        GameRules gameRules = world.getGameRules();
        gameRules.get(GameRules.SNOW_ACCUMULATION_HEIGHT).set(0, server);
        gameRules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
    }

    private void randomizeWorldConditions(ServerWorld world) {
        if (random.nextFloat() <= DAY_TIME_CHANCE) {
            world.setTimeOfDay(6000);
        } else {
            world.setTimeOfDay(18000);
        }

        if (random.nextFloat() <= CLEAR_WEATHER_CHANCE) {
            world.setWeather(1000, 0, false, false);
        } else {
            boolean thunder = random.nextFloat() <= (THUNDER_CHANCE / CLEAR_WEATHER_CHANCE);  // conditional probability
            world.setWeather(0, 1000, true, thunder);
        }
    }

    private void tick() {

    }
}
