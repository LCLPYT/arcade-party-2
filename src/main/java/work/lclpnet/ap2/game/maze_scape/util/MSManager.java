package work.lclpnet.ap2.game.maze_scape.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.LookAtMobTask;
import net.minecraft.entity.ai.brain.task.MeleeAttackTask;
import net.minecraft.entity.ai.brain.task.RangedApproachTask;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.hook.BrainCreationCallback;
import work.lclpnet.ap2.core.hook.CobwebSlowCallback;
import work.lclpnet.ap2.core.hook.EntityPathFindingCallback;
import work.lclpnet.ap2.core.hook.LivingEntityAttributeInitCallback;
import work.lclpnet.ap2.core.mixin.EntityNavigationAccessor;
import work.lclpnet.ap2.core.type.ApEntity;
import work.lclpnet.ap2.core.type.ApLandPathNodeMaker;
import work.lclpnet.ap2.core.type.ApMobNavigation;
import work.lclpnet.ap2.core.type.WardenBrainHandle;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.monster.EndermanData;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterData;
import work.lclpnet.ap2.game.maze_scape.monster.SpiderData;
import work.lclpnet.ap2.game.maze_scape.monster.WardenData;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.impl.ai.BlockedPathFindingPredicate;
import work.lclpnet.ap2.impl.ai.CollisionPathFindingPredicate;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.ap2.impl.util.world.ChunkPersistence;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class MSManager {

    private static final int MOB_COUNT = 3;
    private final ServerWorld world;
    private final MSStruct struct;
    private final Participants participants;
    private final Random random;
    private final Logger logger;
    private final MSTargetManager targetManager;
    private final int mapChunkRadius;
    private final Map<UUID, MonsterData> monsters = new HashMap<>();

    public MSManager(ServerWorld world, GameMap map, MSStruct struct, Participants participants, Random random, Logger logger) {
        this.world = world;
        this.struct = struct;
        this.participants = participants;
        this.random = random;
        this.logger = logger;

        targetManager = new MSTargetManager(struct, participants, world);
        mapChunkRadius = MSGenerator.getMaxChunkSize(map);
    }

    public ServerWorld world() {
        return world;
    }

    public MSStruct struct() {
        return struct;
    }

    public void init(MiniGameHandle gameHandle) {
        var hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(LivingEntityAttributeInitCallback.HOOK, this::initAttributes);
        hooks.registerHook(BrainCreationCallback.Warden.HOOK, this::createWardenBrain);
        hooks.registerHook(EntityPathFindingCallback.HOOK, this::modifyPathFinding);
        hooks.registerHook(CobwebSlowCallback.HOOK, this::cancelCobwebSlow);

        var persistence = new ChunkPersistence(world, gameHandle);
        persistence.markQuadPersistent(-mapChunkRadius, -mapChunkRadius, mapChunkRadius, mapChunkRadius);
    }

    public void spawnMobs() {
        // find most distant nodes where the monsters may spawn
        var spawns = mostDistantSpawns();

        if (spawns.size() < MOB_COUNT) {
            logger.error("Failed to find {} spawn points for mobs", MOB_COUNT);
            return;
        }

        spawnWarden(spawns.getFirst());
//        spawnSpider(spawns.get(1));
//        spawnEnderman(spawns.get(2));

        targetManager.update();
    }

    public List<Vec3d> mostDistantSpawns() {
        // TODO use astar path finder to calculate distance
        // TODO spread mobs among different directions / branches of the structure
        var playerNodes = playerNodes();
        var distanceCalculator = struct.distanceCalculator();

        var nodes = struct.graph().nodes();
        Object2IntMap<Object> distances = new Object2IntOpenHashMap<>(nodes.size());

        for (var node : nodes) {
            int minDistance = Integer.MAX_VALUE;

            for (var playerNode : playerNodes) {
                int distance = distanceCalculator.distance(playerNode, node);

                if (distance < minDistance) {
                    minDistance = distance;
                }
            }

            if (minDistance == Integer.MAX_VALUE) continue;

            distances.put(node, minDistance);
        }

        nodes.sort(Comparator.comparingInt(node -> distances.getOrDefault(node, Integer.MIN_VALUE)).reversed());

        return nodes.stream()
                .flatMap(node -> Optional.ofNullable(node.oriented())
                        .map(OrientedStructurePiece::spawn)
                        .stream())
                .limit(MOB_COUNT)
                .toList();
    }

    private Set<Node<Connector3, StructurePiece, OrientedStructurePiece>> playerNodes() {
        Set<Node<Connector3, StructurePiece, OrientedStructurePiece>> playerNodes = new HashSet<>();

        for (ServerPlayerEntity player : participants) {
            var node = struct.nodeAt(player.getPos());

            if (node != null) {
                playerNodes.add(node);
            }
        }

        return playerNodes;
    }

    private void spawnWarden(Vec3d pos) {
        WardenEntity warden = new WardenEntity(EntityType.WARDEN, world);

        configureMobCommon(pos, warden);

        var brain = warden.getBrain();
        brain.setTaskList(Activity.EMERGE, 5, ImmutableList.of(), MemoryModuleType.IS_EMERGING);
        brain.setTaskList(Activity.DIG, 5, ImmutableList.of(), MemoryModuleType.DIG_COOLDOWN);
        brain.resetPossibleActivities();

        world.spawnEntity(warden);

        UUID uuid = warden.getUuid();
        monsters.put(uuid, new WardenData(uuid, this, logger));

        targetManager.addMonster(warden);
    }

    private void spawnSpider(Vec3d pos) {
        SpiderEntity spider = new SpiderEntity(EntityType.SPIDER, world);

        configureMobCommon(pos, spider);

        world.spawnEntity(spider);

        UUID uuid = spider.getUuid();
        monsters.put(uuid, new SpiderData(uuid, this, logger, random));

        targetManager.addMonster(spider);
    }

    private void spawnEnderman(Vec3d pos) {
        EndermanEntity enderman = new EndermanEntity(EntityType.ENDERMAN, world);

        configureMobCommon(pos, enderman);

        world.spawnEntity(enderman);

        UUID uuid = enderman.getUuid();
        monsters.put(uuid, new EndermanData(uuid, this, logger));

        targetManager.addMonster(enderman);
    }

    private void configureMobCommon(Vec3d pos, MobEntity entity) {
        entity.setPosition(pos);
        entity.setInvulnerable(true);
        entity.setPersistent();
        entity.setGlowing(true);
        entity.setOnGround(true);  // required to perform path finding immediately

        EntityUtil.setAttribute(entity, EntityAttributes.GENERIC_STEP_HEIGHT, 2);

        EntityNavigation navigation = entity.getNavigation();
        navigation.setRangeMultiplier(8f);

        if (navigation instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanWalkOverFences(true);
            nav.setCanEnterOpenDoors(true);
        }

        if (navigation instanceof ApMobNavigation nav) {
            nav.ap2$patchTrapdoorPathFindingTarget();
        }

        ApEntity apEntity = (ApEntity) entity;

        // fix warden getting stuck on narrow blocks, like open trapdoors on walls / as railings
        apEntity.ap2$patchNarrowMovement();

        // fix continuous jumping when walking by open trapdoors
        apEntity.ap2$patchTrapdoorJumping();

        // adjust PathNodeMaker
        PathNodeMaker nodeMaker = ((EntityNavigationAccessor) navigation).getNodeMaker();

        if (nodeMaker instanceof ApLandPathNodeMaker apPathMaker) {
            apPathMaker.ap2$addPathFindingPredicate(BlockedPathFindingPredicate.getInstance());
            apPathMaker.ap2$addPathFindingPredicate(CollisionPathFindingPredicate.getInstance());
            apPathMaker.ap2$addPathFindingPredicate(new PitPathFindingPredicate(struct));
        }
    }

    private void initAttributes(LivingEntity entity) {
        if (entity.getWorld() != world || !isMonsterType(entity)) return;

        // make sure monsters can track down players everywhere in the map
        EntityUtil.setAttribute(entity, EntityAttributes.GENERIC_FOLLOW_RANGE, 2 * mapChunkRadius * 16);
    }

    private static boolean isMonsterType(LivingEntity entity) {
        return entity instanceof WardenEntity
                || entity instanceof SpiderEntity
                || entity instanceof EndermanEntity;
    }

    private @Nullable Brain<WardenEntity> createWardenBrain(WardenEntity warden, Dynamic<?> dynamic, Supplier<WardenBrainHandle> handleGetter) {
        if (warden.getWorld() != world) return null;

        WardenBrainHandle handle = handleGetter.get();

        // adjusted activities from WardenBrain::create
        var brain = handle.deserialize(dynamic);

        handle.addCoreActivities(brain);  // don't add emerge and dig activities
        handle.addIdleActivities(brain);
        handle.addRoarActivities(brain);
        handle.addInvestigateActivities(brain);
        handle.addSniffActivities(brain);

        // custom fight activity
        brain.setTaskList(
                Activity.FIGHT,
                10,
                ImmutableList.of(
                        LookAtMobTask.create(entity -> isTargeting(warden, entity), (float)warden.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE)),
                        RangedApproachTask.create(1.2F),
                        MeleeAttackTask.create(18)
                ),
                MemoryModuleType.ATTACK_TARGET
        );

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.resetPossibleActivities();

        return brain;
    }

    private static boolean isTargeting(WardenEntity warden, LivingEntity entity) {
        return warden.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).filter(x -> x == entity).isPresent();
    }

    public void updateMobs() {
        targetManager.update();
    }

    public void tick() {
        for (var data : monsters.values()) {
            data.tick();
        }
    }

    private @Nullable Path modifyPathFinding(Entity entity, @Nullable Path path, Set<BlockPos> targets, Function<BlockPos, @Nullable Path> pathFinder) {
        if (!monsters.containsKey(entity.getUuid()) || (path != null && path.reachesTarget())) {
            return path;
        }

        // try to find the shortest partial path, as the no real target could directly be reached
        return targets.stream()
                .map(target -> findPartialPath(entity, target, pathFinder))
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(Path::getLength))
                .orElse(path);
    }

    private @Nullable Path findPartialPath(Entity entity, BlockPos target, Function<BlockPos, Path> pathFinder) {
        var passagePath = findPassagePath(entity, target);

        if (passagePath.isEmpty()) {
            return null;
        }

        // try to find partial path towards the passage on half the way
        final int size = passagePath.size();
        int i = (size / 2) - 1;

        while (i >= 0 && i < size) {
            Passage passage = passagePath.get(i);
            Path partial = pathFinder.apply(passage.pos());

            if (partial != null && partial.reachesTarget()) {
                return partial;
            }

            // no path could be found, try to half the way again
            i = ((i + 1) / 2) - 1;
        }

        return null;
    }

    public @NotNull List<Passage> findPassagePath(Entity entity, BlockPos target) {
        Vec3d entityPos = entity.getPos();
        Vec3d targetPos = target.toBottomCenterPos();

        var entityNode = struct.nodeAt(entityPos);
        var targetNode = struct.nodeAt(targetPos);

        if (entityNode == null || targetNode == null || entityNode == targetNode) {
            return List.of();
        }

        Passage start = struct.nearestPassageTo(entityPos, entityNode);
        Passage end = struct.nearestPassageTo(targetPos, targetNode);

        if (start == null || end == null) {
            return List.of();
        }

        return struct.passagePathFinder().findPath(start, end);
    }

    private boolean cancelCobwebSlow(Entity entity, BlockPos blockPos) {
        return entity.getWorld() == world && monsters.containsKey(entity.getUuid());
    }

    public Participants participants() {
        return participants;
    }

    public void onKillAcquired(Entity entity) {
        MonsterData data = monsters.get(entity.getUuid());

        if (data == null) return;

        data.onKillAcquired();
    }
}
