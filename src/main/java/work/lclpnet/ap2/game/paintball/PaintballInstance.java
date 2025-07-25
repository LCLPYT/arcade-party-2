package work.lclpnet.ap2.game.paintball;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.json.JSONObject;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback;
import work.lclpnet.ap2.game.paintball.kit.RifleKit;
import work.lclpnet.ap2.game.paintball.kit.ShotgunKit;
import work.lclpnet.ap2.game.paintball.kit.SniperKit;
import work.lclpnet.ap2.game.paintball.util.*;
import work.lclpnet.ap2.impl.game.TeamGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.game.kit.KitHandler;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.ServerWorldMountContext;
import work.lclpnet.ap2.impl.scene.simulation.EntityCollisionManager;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.TickMovementObserver;
import work.lclpnet.ap2.impl.util.handler.VisualCooldown;
import work.lclpnet.ap2.impl.util.title.AnimatedTitle;
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.cache.ChunkCache;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.generator.TerrainGenerator;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.util.PlayerReset;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor;
import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class PaintballInstance extends TeamGameInstance implements MapBootstrapFunction {

    private static final int
            MIN_DURATION_SECONDS = 120,
            MAX_DURATION_SECONDS = 180,
            RESULT_DELAY_TICKS = Ticks.seconds(3);

    private final IntScoreDataContainer<Team, TeamRef> data = new IntScoreDataContainer<>(this::createReference, Ordering.DESCENDING, "game.ap2.paintball.blocks_painted");
    private final Random random = new Random();
    private final TickMovementObserver movementObserver;
    private final VisualCooldown respawnCooldown;

    private PaintManager paintManager;
    private KitHandler kitHandler;
    private PaintballTeams teams;
    private ResetBlockWorldModifier baseWalls;
    private PaintGunManager paintGunManager;
    private ResultSpot resultSpot;
    private boolean started = false;

    public PaintballInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        var collisionDetector = new ChunkedCollisionDetector();
        movementObserver = new TickMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);

        getTeamManager().setUseColorCodes(true);

        respawnCooldown = new VisualCooldown(gameHandle.getGameScheduler());
    }

    @Override
    protected DataContainer<Team, TeamRef> getData() {
        return data;
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        teams = new PaintballTeams(getTeamManager(), map, gameHandle.getParticipants(), random, gameHandle.getLogger());
        teams.setup();

        Scene scene = new Scene(new ServerWorldMountContext(world));
        scene.animate(1, gameHandle.getScheduler());

        BlockShape bounds = MapUtil.readShape(map, "bounds");

        paintManager = new PaintManager(world, teams, getTeamManager(), data, bounds);
        paintGunManager = new PaintGunManager(world, scene, paintManager, teams, random, gameHandle.getParticipants(),
                gameHandle.getTranslations(), winManager::isGameOver);

        paintGunManager.init(gameHandle.getHookRegistrar());

        replaceTemplateColors(world);
        buildMapCollisions(world, bounds);
        closeBases(world);

        paintManager.countBlocks();

        resultSpot = ResultSpot.fromJson(map.getProperties().getJSONObject("result-spot"));
    }

    private void buildMapCollisions(ServerWorld world, BlockShape bounds) {
        var space = MinecraftSpace.get(world);
        space.setAutoLoadTerrain(false);

        ChunkCache chunkCache = space.getChunkCache();

        for (BlockPos pos : bounds) {
            chunkCache.loadData(pos.toImmutable());
        }

        // TODO to be optimized using greedy meshing

        // needs to be running on the physics thread
        CompletableFuture.runAsync(() -> {
            for (BlockPos pos : bounds) {
                TerrainGenerator.load(space, pos);
            }
        }, space.getWorkerThread()).join();
    }

    private void replaceTemplateColors(ServerWorld world) {
        for (PaintballTeam team : teams) {
            DyeTeamKey template = team.templateColor();

            for (BlockPos pos : team.baseBounds()) {
                BlockState state = world.getBlockState(pos);
                Paintable paintable = paintManager.paintable(state.getBlock());

                if (paintable == null || !state.isOf(paintable.blockFor(template))) continue;

                paintManager.replace(pos, state, paintable, team.key());
            }
        }
    }

    @Override
    protected void prepare() {
        getTeamManager()
                .partitionIntoTeams(gameHandle.getParticipants(), teams.stream()
                .map(PaintballTeam::key)
                .collect(Collectors.toSet()));

        movementObserver.init(gameHandle.getGameScheduler(), gameHandle.getHookRegistrar(), gameHandle.getServer());

        teleportTeamsToSpawns();
        equipPlayers();
        setupKits();
        setupPlayerCollisions();
        balanceTeams();

        commons().gameRuleBuilder()
                .set(GameRules.NATURAL_REGENERATION, false)
                .set(GameRules.FALL_DAMAGE, false);
    }

    private void setupPlayerCollisions() {
        var entityCollisions = new EntityCollisionManager(getWorld(), gameHandle::getParticipants);
        entityCollisions.init(gameHandle.getScheduler());

        TeamManager teamManager = getTeamManager();

        teams.forEach(pbt -> teamManager.getTeam(pbt).ifPresent(team -> {
            int group = teams.playerGroup(pbt);

            for (ServerPlayerEntity player : team.getPlayers()) {
                entityCollisions.getRigidBody(player).ifPresent(rb -> rb.setCollisionGroup(group));
            }
        }));
    }

    private void setupKits() {
        kitHandler = KitHandler.create(gameHandle, getWorld(), kitHandle -> List.of(
                new RifleKit(kitHandle, paintGunManager),
                new ShotgunKit(kitHandle, paintGunManager),
                new SniperKit(kitHandle, paintGunManager)
        ));

        kitHandler.setup();

        for (PaintballTeam pbt : teams) {
            movementObserver.whenEntering(pbt.baseBounds(), player -> {
                if (teams.isMember(pbt, player)) {
                    kitHandler.enableKitChanger(player);
                }
            });

            movementObserver.whenLeaving(pbt.baseBounds(), player -> {
                if (teams.isMember(pbt, player)) {
                    kitHandler.disableKitChanger(player);
                }
            });
        }
    }

    private void equipPlayers() {
        for (PaintballTeam instance : teams) {
            Team team = getTeamManager().getTeam(instance).orElse(null);

            if (team == null) continue;

            int color = instance.key().color();

            for (ServerPlayerEntity player : team.getPlayers()) {
                player.equipStack(EquipmentSlot.HEAD, unbreakable(getLeatherArmor(Items.LEATHER_HELMET, color)));
                player.equipStack(EquipmentSlot.CHEST, unbreakable(getLeatherArmor(Items.LEATHER_CHESTPLATE, color)));
                player.equipStack(EquipmentSlot.LEGS, unbreakable(getLeatherArmor(Items.LEATHER_LEGGINGS, color)));
                player.equipStack(EquipmentSlot.FEET, unbreakable(getLeatherArmor(Items.LEATHER_BOOTS, color)));
            }
        }
    }

    @Override
    protected void afterInitialDelay() {
        kitHandler.startKitSelectionTimer(commons(), super::afterInitialDelay);
    }

    @Override
    protected void ready() {
        openBases();

        kitHandler.closeKitChanger();
        kitHandler.selectKitItem();

        setupRespawnCooldown();
        configureHooksAndProtector();

        paintGunManager.setShootingEnabled(true);

        var subject = gameHandle.getTranslations().translateText(gameHandle.getGameInfo().getTaskKey());

        int duration = MIN_DURATION_SECONDS + random.nextInt(MAX_DURATION_SECONDS - MIN_DURATION_SECONDS + 1);
        commons().createTimer(subject, duration).whenDone(this::beginResults);

        started = true;
    }

    private void configureHooksAndProtector() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, this::onDamage);

        hooks.registerHook(SpectatePlayerCallback.HOOK, (spectator, target)
                -> gameHandle.getParticipants().isParticipating(spectator));

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.EXPLOSION);
            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source)
                    -> entity instanceof ServerPlayerEntity player
                    && gameHandle.getParticipants().isParticipating(player)
                    && (source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.EXPLOSION)));
        });
    }

    private void setupRespawnCooldown() {
        TaskScheduler scheduler = gameHandle.getGameScheduler();

        respawnCooldown.setOnCooldownOver(player -> {
            teams.teamOf(player).ifPresent(pbt -> teleportToTeamSpawn(player, pbt));

            player.setHealth(player.getMaxHealth());
            player.getAbilities().setFlySpeed(0);
            player.sendAbilitiesUpdate();

            // delay game mode change one tick to prevent other players from seeing the teleport
            scheduler.immediate(() -> {
                player.getAbilities().setFlySpeed(0.05f);
                player.sendAbilitiesUpdate();

                player.changeGameMode(gameHandle.getPlayerUtil().getDefaultGameMode());
            });
        });
    }

    private void closeBases(ServerWorld world) {
        int flags = Block.NOTIFY_LISTENERS | Block.SKIP_DROPS | Block.FORCE_STATE;

        baseWalls = new ResetBlockWorldModifier(world, flags);

        for (PaintballTeam team : teams) {
            BlockBox bounds = team.baseBounds();

            for (BlockPos pos : bounds) {
                if (!bounds.isBorder(pos)) continue;

                BlockState state = world.getBlockState(pos);

                if (!state.getCollisionShape(world, pos).isEmpty()) continue;

                baseWalls.setBlockState(pos, Blocks.BARRIER.getDefaultState(), flags);
            }
        }
    }

    private void openBases() {
        if (baseWalls != null) {
            baseWalls.undo();
        }
    }

    @Override
    protected void teleportTeamsToSpawns() {
        for (PaintballTeam pbt : teams) {
            Team team = getTeamManager().getTeam(pbt).orElse(null);

            if (team == null) continue;

            for (ServerPlayerEntity player : team.getPlayers()) {
                teleportToTeamSpawn(player, pbt);
            }
        }
    }

    private void teleportToTeamSpawn(ServerPlayerEntity player, PaintballTeam pbt) {
        Vec3d pos = pbt.spawn();

        player.teleport(getWorld(), pos.getX(), pos.getY(), pos.getZ(), Set.of(), pbt.yaw(), 0, true);
    }

    private boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
        if (winManager.isGameOver()
                || !(entity instanceof ServerPlayerEntity player)
                || !gameHandle.getParticipants().isParticipating(player)) return false;

        PaintballTeam team = teams.teamOf(player).orElse(null);

        // prevent damage in base
        if (team == null || team.baseBounds().contains(player.getPos())) return false;

        // respect hurt time, except for explosions
        if (!source.isOf(DamageTypes.EXPLOSION) && player.hurtTime > 0) {
            return false;
        }

        if ((player.getHealth() - amount) <= 0) {
            onLethalDamage(source, player, amount);
            return false;
        }

        return true;
    }

    private void onLethalDamage(DamageSource source, ServerPlayerEntity player, float amount) {
        player.getDamageTracker().onDamage(source, amount);

        gameHandle.getDeathMessages().getDeathMessage(player, source)
                .sendTo(PlayerLookup.all(gameHandle.getServer()));

        getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 0.8f, 0.8f);

        player.changeGameMode(GameMode.SPECTATOR);
        player.setHealth(20);

        respawnCooldown.setCooldown(player, 50);
    }

    private void beginResults() {
        gameHandle.resetGameScheduler();

        paintGunManager.setShootingEnabled(false);
        paintManager.freeze();

        teleportPlayersToResults();

        gameHandle.getGameScheduler().interval(this::teleportPlayersToResults, 1).whenComplete(() -> {
        });

        commons().announcer().announce("game.ap2.paintball.game_over", null);

        gameHandle.getGameScheduler().timeout(this::showResults, RESULT_DELAY_TICKS);
    }

    private void teleportPlayersToResults() {
        ServerWorld world = getWorld();
        Vec3d pos = resultSpot.pos;

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            player.changeGameMode(GameMode.SPECTATOR);
            player.getAbilities().setFlySpeed(0);
            player.sendAbilitiesUpdate();

            player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), Set.of(), resultSpot.yaw, resultSpot.pitch, true);
        }
    }

    private void showResults() {
        var animatedTitle = new AnimatedTitle();

        List<TeamRef> teamRefs = teams.stream()
                .map(getTeamManager()::getTeam)
                .flatMap(Optional::stream)
                .map(this::createReference)
                .toList();

        animatedTitle.add(new PaintballResultAnimation(teamRefs, data, gameHandle.getServer(), gameHandle.getTranslations(), () -> {
            for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
                player.changeGameMode(GameMode.SPECTATOR);
                player.getAbilities().setFlySpeed(0.05f);
                player.sendAbilitiesUpdate();
            }

            winManager.complete();
        }));

        animatedTitle.start(gameHandle.getGameScheduler(), 1);

        gameHandle.whenDone(animatedTitle::stop);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        balanceTeams();
    }

    private void balanceTeams() {
        for (PaintballTeam team : teams) {
            Set<ServerPlayerEntity> players = team.participants(getTeamManager(), gameHandle.getParticipants());

            if (players.isEmpty()) continue;

            int deficit = teams.playerDeficit(team);
            double extraHealthPerPlayer = deficit * 20.d / players.size();

            for (ServerPlayerEntity player : players) {
                PlayerReset.setAttribute(player, EntityAttributes.MAX_HEALTH, 20.d + extraHealthPerPlayer);
            }
        }

        if (started) return;

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private record ResultSpot(Vec3d pos, float yaw, float pitch) {

        public static ResultSpot fromJson(JSONObject json) {
            Vec3d pos = MapUtil.readCenteredVec3d(json.getJSONArray("pos"));
            float yaw = MapUtil.readAngle(json.optNumber("yaw", 0));
            float pitch = MapUtil.readAngle(json.optNumber("pitch", 0));

            return new ResultSpot(pos, yaw, pitch);
        }
    }
}
