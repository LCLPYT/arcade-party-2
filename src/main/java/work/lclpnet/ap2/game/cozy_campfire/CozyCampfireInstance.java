package work.lclpnet.ap2.game.cozy_campfire;

import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.game.cozy_campfire.setup.*;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;
import work.lclpnet.ap2.impl.util.TeamStorage;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedTeamBossBar;
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
    private final TeamStorage<CampfireFuel> campfireFuel = TeamStorage.create(() -> new CampfireFuel(this.startingFuel));
    private CCHooks hookSetup;
    private CCFuel fuel;
    private DynamicTranslatedTeamBossBar bossBar;
    private int fuelPerSecond = 100, startingFuelSeconds = 30;
    private int fuelPerMinute, startingFuel;
    private int time = 0;

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

        readMapFuelInfo();
        CCBaseManager baseManager = readBases(teamManager);

        fuel = new CCFuel(getWorld(), baseManager);
        fuel.registerFuel(fuelPerSecond);

        teleportTeamsToSpawns();

        CCKitManager kitManager = new CCKitManager(teamManager, getWorld(), random);

        for (ServerPlayerEntity player : participants) {
            kitManager.giveItems(player);
        }

        TranslationService translations = gameHandle.getTranslations();
        var args = new CCHooks.Args(fuel, baseManager, kitManager, this::onAddFuel);

        hookSetup = new CCHooks(participants, teamManager, this, translations, args);
        hookSetup.register(gameHandle.getHookRegistrar());

        setupMovementObserver(hookSetup);

        DynamicTranslatedPlayerBossBar playerBossBar = usePlayerDynamicTaskDisplay(getTimeArgument(startingFuel));
        bossBar = new DynamicTranslatedTeamBossBar(playerBossBar, teamManager);
    }

    @Override
    protected void ready() {
        gameHandle.protect(hookSetup::configure);

        gameHandle.getGameScheduler().interval(this::tick, 1);
    }

    private void setupMovementObserver(CCHooks hookSetup) {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        MinecraftServer server = gameHandle.getServer();

        movementObserver.init(hooks, server);

        hookSetup.configureBaseRegionEvents(collisionDetector, movementObserver);
    }

    private void readMapFuelInfo() {
        Number perSecond = getMap().getProperty("fuel-per-second");
        Number startSeconds = getMap().getProperty("start-fuel-seconds");

        if (perSecond != null) {
            this.fuelPerSecond = perSecond.intValue();
        }

        if (startSeconds != null) {
            this.startingFuelSeconds = startSeconds.intValue();
        }

        this.fuelPerMinute = this.fuelPerSecond * 60;
        this.startingFuel = this.fuelPerSecond * this.startingFuelSeconds;
    }

    private CCBaseManager readBases(TeamManager teamManager) {
        CCReader setup = new CCReader(getMap(), gameHandle.getLogger());
        var bases = setup.readBases(teamManager.getTeams());

        return new CCBaseManager(bases, teamManager);
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

    private Object getTimeArgument(int fuel) {
        int minutes = fuel / fuelPerMinute;
        int seconds = fuel % fuelPerMinute / fuelPerSecond;

        return gameHandle.getTranslations().translateText("game.ap2.cozy_campfire.time", minutes, seconds)
                .formatted(Formatting.YELLOW);
    }

    private void tick() {
        if (++time % 20 == 0) {
            everySecond();
        }
    }

    private void everySecond() {
        for (Team team : getTeamManager().getTeams()) {
            CampfireFuel fuel = campfireFuel.getOrCreate(team);
            fuel.set(fuel.count - fuelPerSecond);

            updateBossBar(team, fuel);

            if (fuel.count <= 0) {
                eliminate(team);
            }
        }
    }

    private void updateBossBar(Team team, CampfireFuel fuel) {
        bossBar.setArgument(team, 0, getTimeArgument(fuel.count));
        bossBar.setPercent(team, fuel.percent());
    }

    public void onAddFuel(ServerPlayerEntity player, BlockPos pos, Team team, ItemStack stack) {
        int value = this.fuel.getValue(stack);

        stack.setCount(0);

        if (player.getWorld() instanceof ServerWorld world) {
            double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ();

            world.playSound(null, x, y, z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.2f, 1f);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 7, 0.1, 0.1, 0.1, 0);
        }

        if (value <= 0) return;

        CampfireFuel fuel = campfireFuel.getOrCreate(team);
        fuel.set(fuel.count + value);

        updateBossBar(team, fuel);
    }

    private static class CampfireFuel {
        int count;
        int max = count;

        public CampfireFuel(int initial) {
            this.count = initial;
        }

        void set(int count) {
            this.count = count;
            if (count > max) max = count;
        }

        float percent() {
            if (max == 0) return 0f;

            return MathHelper.clamp((float) count / max, 0f, 1f);
        }
    }
}
