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
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.game.cozy_campfire.setup.*;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;
import work.lclpnet.ap2.impl.util.TeamStorage;
import work.lclpnet.ap2.impl.util.TimeHelper;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedTeamBossBar;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.PlayerMovementObserver;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.LocalizedFormat;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class CozyCampfireInstance extends TeamEliminationGameInstance implements MapBootstrapFunction {

    private static final float DAY_TIME_CHANCE = 0.25f, CLEAR_WEATHER_CHANCE = 0.6f, THUNDER_CHANCE = 0.05f;
    public static final TeamKey TEAM_RED = ApTeamKeys.RED, TEAM_BLUE = ApTeamKeys.BLUE;
    private final Random random = new Random();
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver;
    private final TeamStorage<CampfireFuel> campfireFuel = TeamStorage.create(this::createCampfireFuel);
    private final Set<Team> toEliminate = new HashSet<>();
    private CCHooks hookSetup;
    private CCFuel fuel;
    private DynamicTranslatedTeamBossBar bossBar;
    private float teamBias = 0.8f;
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
    public void bootstrapWorld(ServerWorld world, GameMap map) {
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
            PlayerUtil.modifyWalkSpeed(player, 0.15f);
            player.sendAbilitiesUpdate();
        }

        TranslationService translations = gameHandle.getTranslations();
        var args = new CCHooks.Args(fuel, baseManager, kitManager, this::onAddFuel);

        hookSetup = new CCHooks(participants, teamManager, this, translations, args);
        hookSetup.register(gameHandle.getHookRegistrar());

        setupMovementObserver(hookSetup);

        Object time = getTimeArgument(startingFuel, 1);  // startingFuel is defined for a single one player
        DynamicTranslatedPlayerBossBar playerBossBar = usePlayerDynamicTaskDisplay(time);
        bossBar = new DynamicTranslatedTeamBossBar(playerBossBar, teamManager);
    }

    @Override
    protected void ready() {
        gameHandle.protect(hookSetup::configure);

        gameHandle.getGameScheduler().interval(this::tick, 1);
    }

    @Override
    public void teamEliminated(Team team) {
        var participatingTeams = getTeamManager().getParticipatingTeams();

        if (participatingTeams.size() == 1) {
            // there is only one team remaining, which will win after the super method call
            // put a detail message into the elimination data container now, before the win manager does
            Team lastTeam = participatingTeams.iterator().next();

            TranslationService translations = gameHandle.getTranslations();
            CampfireFuel fuel = campfireFuel.getOrCreate(lastTeam);
            int remainingSeconds = getRemainingTime(fuel.count, lastTeam.getPlayerCount());

            var duration = TimeHelper.formatTime(translations, remainingSeconds);
            var detail = translations.translateText("game.ap2.cozy_campfire.remaining", duration);

            getData().eliminated(lastTeam, detail);
        }

        super.teamEliminated(team);
    }

    private void setupMovementObserver(CCHooks hookSetup) {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        MinecraftServer server = gameHandle.getServer();

        movementObserver.init(hooks, server);

        hookSetup.configureBaseRegionEvents(collisionDetector, movementObserver);
    }

    private void readMapFuelInfo() {
        GameMap map = getMap();
        Number perSecond = map.getProperty("fuel-per-second");
        Number startSeconds = map.getProperty("start-fuel-seconds");
        Number teamBias = map.getProperty("team-bias");

        if (perSecond != null) {
            this.fuelPerSecond = perSecond.intValue();
        }

        if (startSeconds != null) {
            this.startingFuelSeconds = startSeconds.intValue();
        }

        if (teamBias != null) {
            this.teamBias = Math.max(0, teamBias.floatValue());
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

    private float playersFactor(Team team) {
        return playersFactor(team.getPlayerCount());
    }

    private float playersFactor(int players) {
        return Math.max(1f, players * teamBias);
    }

    private Object getTimeArgument(int fuel, int playerCount) {
        int seconds = getRemainingTime(fuel, playerCount);
        int minutes = seconds / 60;
        seconds %= 60;

        return gameHandle.getTranslations().translateText("game.ap2.cozy_campfire.time", minutes, seconds)
                .formatted(Formatting.YELLOW);
    }

    private int getRemainingTime(int fuel, int playerCount) {
        int normalizedFuel = Math.round(fuel / playersFactor(playerCount));
        int minutes = normalizedFuel / fuelPerMinute;
        int seconds = normalizedFuel % fuelPerMinute / fuelPerSecond;

        return minutes * 60 + seconds;
    }

    private void tick() {
        if (++time % 20 == 0) {
            everySecond();
        }
    }

    private void everySecond() {
        toEliminate.clear();

        for (Team team : getTeamManager().getTeams()) {
            CampfireFuel fuel = campfireFuel.getOrCreate(team);

            int fuelConsumption = getFuelConsumption(team);
            fuel.set(fuel.count - fuelConsumption);

            updateBossBar(team, fuel);

            if (fuel.count <= 0) {
                toEliminate.add(team);
            }
        }

        if (toEliminate.isEmpty()) return;

        TranslationService translations = gameHandle.getTranslations();
        int timeSurvived = time / 20;
        var duration = TimeHelper.formatTime(translations, timeSurvived);
        var detail = translations.translateText("game.ap2.cozy_campfire.survived", duration);

        eliminateAll(toEliminate, detail);
    }

    private int getFuelConsumption(Team team) {
        return Math.round(fuelPerSecond * playersFactor(team));
    }

    private void updateBossBar(Team team, CampfireFuel fuel) {
        Object time = getTimeArgument(fuel.count, team.getPlayerCount());
        bossBar.setArgument(team, 0, time);
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

        notifyTeamMembers(team, value);
    }

    private void notifyTeamMembers(Team team, int value) {
        TranslationService translations = gameHandle.getTranslations();

        var added = LocalizedFormat.format("%.2f", (float) value / (fuelPerSecond * playersFactor(team)));
        var msg = translations.translateText("game.ap2.cozy_campfire.fuel_added", styled(added, Formatting.YELLOW))
                .formatted(Formatting.GREEN);

        for (ServerPlayerEntity player : team.getPlayers()) {
            player.sendMessage(msg.translateFor(player), true);
            player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.8f);
        }
    }

    private CampfireFuel createCampfireFuel(Team team) {
        return new CampfireFuel(Math.round(this.startingFuel * playersFactor(team)));
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
