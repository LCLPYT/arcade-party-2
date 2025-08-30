package work.lclpnet.ap2.game.paintball;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.api.util.world.WorldScanner;
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback;
import work.lclpnet.ap2.game.paintball.item.InkGrenadeItem;
import work.lclpnet.ap2.game.paintball.item.InkPackItem;
import work.lclpnet.ap2.game.paintball.item.MedKitItem;
import work.lclpnet.ap2.game.paintball.item.TripWireItem;
import work.lclpnet.ap2.game.paintball.kit.RifleKit;
import work.lclpnet.ap2.game.paintball.kit.ShotgunKit;
import work.lclpnet.ap2.game.paintball.kit.SniperKit;
import work.lclpnet.ap2.game.paintball.util.*;
import work.lclpnet.ap2.impl.game.GameCommons;
import work.lclpnet.ap2.impl.game.TeamGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.game.item.SpecialItems;
import work.lclpnet.ap2.impl.game.kit.KitHandler;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.ServerWorldMountContext;
import work.lclpnet.ap2.impl.scene.simulation.EntityCollisionManager;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.VanishManager;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.TickMovementObserver;
import work.lclpnet.ap2.impl.util.handler.VisualCooldown;
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner;
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.cache.ChunkCache;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.generator.TerrainGenerator;
import work.lclpnet.kibu.physics.impl.bullet.thread.PhysicsThread;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.util.PlayerReset;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor;
import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;
import static work.lclpnet.ap2.impl.util.ThreadUtil.submitOn;

public class PaintballInstance extends TeamGameInstance implements MapBootstrapFunction {

    private static final int
            MIN_DURATION_SECONDS = 120,
            MAX_DURATION_SECONDS = 180;

    private final IntScoreDataContainer<Team, TeamRef> data = new IntScoreDataContainer<>(this::createReference, Ordering.DESCENDING, "game.ap2.paintball.blocks_painted");
    private final Random random = new Random();
    private final TickMovementObserver movementObserver;
    private final VisualCooldown respawnCooldown;
    private final VanishManager vanishManager;

    private PaintManager paintManager;
    private KitHandler kitHandler;
    private PaintballTeams teams;
    private ResetBlockWorldModifier baseWalls;
    private PaintGunManager paintGunManager;
    private PaintballResults results;
    private boolean started = false;
    private SpecialItems specialItems;
    private Scene scene;

    public PaintballInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        var collisionDetector = new ChunkedCollisionDetector();
        movementObserver = new TickMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);

        getTeamManager().setUseColorCodes(true);

        respawnCooldown = new VisualCooldown(gameHandle.getGameScheduler());
        vanishManager = VanishManager.setup(gameHandle);
    }

    @Override
    protected DataContainer<Team, TeamRef> getData() {
        return data;
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        teams = new PaintballTeams(getTeamManager(), map, gameHandle.getParticipants(), random, gameHandle.getLogger());
        teams.setup();

        scene = new Scene(new ServerWorldMountContext(world));
        scene.animate(1, gameHandle.getScheduler());

        BlockShape bounds = MapUtil.readShape(map, "bounds");
        GameCommons commons = commons(map, world);

        paintManager = new PaintManager(world, teams, getTeamManager(), data, bounds);
        paintGunManager = new PaintGunManager(world, scene, paintManager, teams, random, gameHandle.getParticipants(),
                gameHandle.getTranslations(), commons.debugController(), winManager::isGameOver);

        paintGunManager.init(gameHandle.getHooks());

        replaceTemplateColors(world);
        buildMapCollisions(world, bounds);
        setupSpecialItems(world, map);
        closeBases(world);

        paintManager.countBlocks();

        var resultSpot = PaintballResults.ResultSpot.fromJson(map.getProperties().getJSONObject("result-spot"));

        results = new PaintballResults(gameHandle, commons.announcer(), world, resultSpot, data, winManager, () -> teams.stream()
                .map(getTeamManager()::getTeam)
                .flatMap(Optional::stream)
                .map(this::createReference)
                .toList());
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
        submitOn(PhysicsThread.get(world), () -> {
            for (BlockPos pos : bounds) {
                TerrainGenerator.load(space, pos);
            }
        }).join();
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

        for (var team : getTeamManager().getMinecraftTeams()) {
            team.setShowFriendlyInvisibles(true);
        }

        movementObserver.init(gameHandle.getGameScheduler(), gameHandle.getHooks(), gameHandle.getServer());

        teleportTeamsToSpawns();
        equipPlayers();
        setupKits();
        setupPlayerCollisions();
        balanceTeams();

        commons().gameRuleBuilder()
                .set(GameRules.NATURAL_REGENERATION, false)
                .set(GameRules.FALL_DAMAGE, false);
    }

    private void setupSpecialItems(ServerWorld world, GameMap map) {
        LongSet validSpawns = findReachablePositions(world, map);

        // remove team spawn from valid spawns, so that items don't spawn in team bases
        for (PaintballTeam team : teams) {
            for (BlockPos pos : team.baseBounds()) {
                validSpawns.remove(pos.asLong());
            }
        }

        BlockPredicate validSpawn = pos -> validSpawns.contains(pos.asLong());

        specialItems = SpecialItems.create(gameHandle, map, world, random, validSpawn, commons(map, world).debugController(), r -> r
                .register(new MedKitItem(), 0.25f)
                .register(new InkGrenadeItem(paintGunManager, scene, random, teams), 0.5f)
                .register(new InkPackItem(paintGunManager), 0.15f)
                .register(new TripWireItem(gameHandle.getTranslations(), gameHandle.getParticipants(), world, teams, paintManager), 0.15f)
        );

        specialItems.setMarkGlowing(true);

        specialItems.setup();
    }

    private @NotNull LongSet findReachablePositions(ServerWorld world, GameMap map) {
        BlockBox bounds = SpecialItems.getSpawnArea(map).bounds();
        BlockPredicate predicate = BlockPredicate.and(bounds::contains, new WalkableBlockPredicate(world));
        AdjacentBlocks adjacent = new SimpleAdjacentBlocks(predicate, 1);
        WorldScanner scanner = new BfsWorldScanner(adjacent);

        LongSet spawns = new LongOpenHashSet();

        PaintballTeam anyTeam = teams.iterator().next();
        BlockPos startPos = BlockPos.ofFloored(anyTeam.spawn());

        scanner.scan(startPos).forEachRemaining(pos -> spawns.add(pos.asLong()));

        return spawns;
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

        paintGunManager.injectKitManager(kitHandler.getManager());
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

        respawnCooldown.setOnCooldownOver(this::respawnPlayer);
        configureHooksAndProtector();

        paintGunManager.setShootingEnabled(true);

        var subject = gameHandle.getTranslations().translateText(gameHandle.getGameInfo().getTaskKey());

        int duration = MIN_DURATION_SECONDS + random.nextInt(MAX_DURATION_SECONDS - MIN_DURATION_SECONDS + 1);
        commons().createTimer(subject, duration).whenDone(this::beginResults);

        started = true;

        var ticker = new PaintballTicker(getWorld(), gameHandle.getParticipants(), teams, paintManager,
                paintGunManager, vanishManager, commons().debugController());

        ticker.start(gameHandle.getGameScheduler(), gameHandle.getHooks());

        specialItems.spawnPeriodically();
    }

    private void beginResults() {
        paintGunManager.setShootingEnabled(false);
        paintManager.freeze();

        results.beginResults();
    }

    private void configureHooksAndProtector() {
        HookRegistrar hooks = gameHandle.getHooks();
        Participants participants = gameHandle.getParticipants();

        hooks.registerHook(SpectatePlayerCallback.HOOK, (spectator, target)
                -> participants.isParticipating(spectator));

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.EXPLOSION);

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source)
                    -> entity instanceof ServerPlayerEntity player
                    && participants.isParticipating(player)
                    && (source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.PLAYER_EXPLOSION)));
        });

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, this::onDamage);
    }

    private void respawnPlayer(ServerPlayerEntity player) {
        teams.teamOf(player).ifPresent(pbt -> teleportToTeamSpawn(player, pbt));

        player.setHealth(player.getMaxHealth());
        PlayerReset.resetAttribute(player, EntityAttributes.MAX_ABSORPTION);
        player.setAbsorptionAmount(0);
        player.getAbilities().setFlySpeed(0);
        player.sendAbilitiesUpdate();

        paintGunManager.refillPaintGun(player);

        // delay game mode change one tick to prevent other players from seeing the teleport
        gameHandle.getGameScheduler().immediate(() -> {
            player.getAbilities().setFlySpeed(0.05f);
            player.sendAbilitiesUpdate();

            player.changeGameMode(gameHandle.getPlayerUtil().getDefaultGameMode());
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
        if (!source.isOf(DamageTypes.PLAYER_EXPLOSION) && player.hurtTime > 0) {
            return false;
        }

        // disallow damaging teammates with explosives
        if (source.isOf(DamageTypes.PLAYER_EXPLOSION) && source.getAttacker() instanceof ServerPlayerEntity attacker
                && getTeamManager().areTeamMates(attacker, player)) {
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

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        balanceTeams();

        super.participantRemoved(player);
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
}
