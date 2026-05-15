package work.lclpnet.ap2.game.cozy_campfire;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.ext.mc.LevelExtensionsKt;
import work.lclpnet.ap2.game.cozy_campfire.setup.*;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;
import work.lclpnet.ap2.impl.util.TeamStorage;
import work.lclpnet.ap2.impl.util.TimeHelper;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedTeamBossBar;
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector;
import work.lclpnet.gaco.collisions.CollisionDetector;
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.game.util.PlayerReset;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.LocalizedFormat;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class CozyCampfireInstance extends TeamEliminationGameInstance implements MapBootstrap {

    private static final float DAY_TIME_CHANCE = 0.55f, RAIN_CHANCE = 0.6f, THUNDER_CHANCE = 0.15f;
    public static final TeamKey TEAM_RED = DyeTeamKey.RED, TEAM_BLUE = DyeTeamKey.BLUE;
    public static final float MOVEMENT_SPEED = 0.15f;
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
    private CCBaseManager baseManager;
    private TeamManager teamManager;

    public CozyCampfireInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useOldCombat();
        useSurvivalMode();

        movementObserver = new PlayerMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);
        getTeamManager().setUseColorCodes(true);  // colored name tags above the player model
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map) {
        teamManager = getTeamManager();
        teamManager.partitionIntoTeams(gameHandle.getParticipants(), Set.of(TEAM_RED, TEAM_BLUE));

        CCReader setup = new CCReader(map, world, gameHandle.getLogger());

        return setup.readBases(teamManager.getTeams())
                .thenAccept(bases -> baseManager = new CCBaseManager(bases, teamManager))
                .thenCompose(_ -> Objects.requireNonNull(world.getServer()).submit(() -> {
                    setupGameRules(map, world);
                    randomizeWorldConditions(world);
                }));
    }

    @Override
    protected void prepare() {
        teamManager.getMinecraftTeams().forEach(team -> {
            team.setAllowFriendlyFire(false);
            team.setSeeFriendlyInvisibles(true);
            team.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.PUSH_OTHER_TEAMS);
        });

        readMapFuelInfo();

        fuel = new CCFuel(getWorld(), baseManager);
        fuel.registerFuel(fuelPerSecond);

        teleportTeamsToSpawns();

        CCKitManager kitManager = new CCKitManager(teamManager, getWorld(), random);
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayer player : participants) {
            kitManager.giveItems(player);
            PlayerReset.modifyWalkSpeed(player, MOVEMENT_SPEED);
        }

        Translations translations = gameHandle.getTranslations();
        var args = new CCHooks.Args(fuel, baseManager, kitManager, this::onAddFuel);

        hookSetup = new CCHooks(participants, teamManager, this, translations, args);
        hookSetup.register(gameHandle.getHooks());

        setupMovementObserver(hookSetup);

        Object time = getTimeArgument(startingFuel, 1);  // startingFuel is defined for a single one player
        DynamicTranslatedPlayerBossBar playerBossBar = usePlayerDynamicTaskDisplay(time);
        bossBar = new DynamicTranslatedTeamBossBar(playerBossBar, teamManager);
    }

    @Override
    protected void go() {
        gameHandle.protect(hookSetup::configure);

        gameHandle.getScheduler().interval(this::tick, 1);

        baseManager.openDoors(getWorld());
    }

    @Override
    public void teamEliminated(Team team) {
        var participatingTeams = getTeamManager().getParticipatingTeams();

        if (participatingTeams.size() == 1) {
            // there is only one team remaining, which will win after the super method call
            // put a detail message into the elimination data container now, before the win manager does
            Team lastTeam = participatingTeams.iterator().next();

            Translations translations = gameHandle.getTranslations();
            CampfireFuel fuel = campfireFuel.get(lastTeam);
            int remainingSeconds = getRemainingTime(fuel.count, lastTeam.getPlayerCount());

            var duration = TimeHelper.formatTime(translations, remainingSeconds);
            var detail = translations.translateText("game.ap2.cozy_campfire.remaining", duration);

            getData().add(lastTeam, detail);
        }

        super.teamEliminated(team);
    }

    private void setupMovementObserver(CCHooks hookSetup) {
        HookRegistrar hooks = gameHandle.getHooks();
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

    private void setupGameRules(GameMap map, ServerLevel world) {
        commons(map, world).gameRuleBuilder()
                .set(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT, 0)
                .set(GameRules.ADVANCE_WEATHER, false)
                .set(GameRules.ADVANCE_TIME, false);
    }

    private void randomizeWorldConditions(ServerLevel world) {
        if (random.nextFloat() <= DAY_TIME_CHANCE) {
            LevelExtensionsKt.setDayTime(world, 6000);
        } else {
            LevelExtensionsKt.setDayTime(world, 18000);
        }

        if (random.nextFloat() <= RAIN_CHANCE) {
            boolean thunder = random.nextFloat() <= (THUNDER_CHANCE / RAIN_CHANCE);  // conditional probability
            LevelExtensionsKt.setWeatherParameters(world, 0, 1000, true, thunder);
        } else {
            LevelExtensionsKt.setWeatherParameters(world, 1000, 0, false, false);
            world.setRainLevel(0);
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
                .formatted(ChatFormatting.YELLOW);
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
            CampfireFuel fuel = campfireFuel.get(team);

            int fuelConsumption = getFuelConsumption(team);
            fuel.set(fuel.count - fuelConsumption);

            updateBossBar(team, fuel);

            if (fuel.count <= 0) {
                toEliminate.add(team);
            }
        }

        if (toEliminate.isEmpty()) return;

        Translations translations = gameHandle.getTranslations();
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

    public void onAddFuel(ServerPlayer player, BlockPos pos, Team team, ItemStack stack) {
        int value = this.fuel.getValue(stack);

        stack.setCount(0);

        if (player.level() instanceof ServerLevel world) {
            double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ();

            world.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.2f, 1f);
            world.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 7, 0.1, 0.1, 0.1, 0);
        }

        if (value <= 0) return;

        CampfireFuel fuel = campfireFuel.get(team);
        fuel.set(fuel.count + value);

        updateBossBar(team, fuel);

        notifyTeamMembers(team, value);
    }

    private void notifyTeamMembers(Team team, int value) {
        Translations translations = gameHandle.getTranslations();

        var added = LocalizedFormat.format("%.2f", (float) value / (fuelPerSecond * playersFactor(team)));
        var msg = translations.translateText("game.ap2.cozy_campfire.fuel_added", styled(added, ChatFormatting.YELLOW))
                .formatted(ChatFormatting.GREEN);

        for (ServerPlayer player : team.getPlayers()) {
            player.sendOverlayMessage(msg.translateFor(player));
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.2f, 1.8f);
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

            return Mth.clamp((float) count / max, 0f, 1f);
        }
    }
}
