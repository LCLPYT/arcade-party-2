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
import work.lclpnet.ap2.core.hook.EntityPathFindingCallback;
import work.lclpnet.ap2.core.hook.LivingEntityAttributeInitCallback;
import work.lclpnet.ap2.core.mixin.EntityNavigationAccessor;
import work.lclpnet.ap2.core.type.ApEntity;
import work.lclpnet.ap2.core.type.ApLandPathNodeMaker;
import work.lclpnet.ap2.core.type.ApMobNavigation;
import work.lclpnet.ap2.core.type.WardenBrainHandle;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
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
    private final Logger logger;
    private final MSTargetManager targetManager;
    private final int mapChunkRadius;
    private final Set<UUID> monsters = new HashSet<>();

    public MSManager(ServerWorld world, GameMap map, MSStruct struct, Participants participants, Logger logger) {
        this.world = world;
        this.struct = struct;
        this.participants = participants;
        this.logger = logger;

        targetManager = new MSTargetManager(struct, participants, world);
        mapChunkRadius = MSGenerator.getMaxChunkSize(map);
    }

    public void init(MiniGameHandle gameHandle) {
        var hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(LivingEntityAttributeInitCallback.HOOK, this::initAttributes);
        hooks.registerHook(BrainCreationCallback.Warden.HOOK, this::createWardenBrain);
        hooks.registerHook(EntityPathFindingCallback.HOOK, this::modifyPathFinding);

        var persistence = new ChunkPersistence(world, gameHandle);
        persistence.markQuadPersistent(-mapChunkRadius, -mapChunkRadius, mapChunkRadius, mapChunkRadius);
    }

    public void spawnMobs() {
        // find most distant nodes where the monsters may spawn
        var spawns = mostDistantSpawns();

        if (spawns.size() < 3) {
            logger.error("Failed to find {} spawn points for mobs", MOB_COUNT);
            return;
        }

        spawnWarden(spawns.getFirst());

        targetManager.update();
    }

    private List<Vec3d> mostDistantSpawns() {
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
        warden.setPosition(pos);
        warden.setInvulnerable(true);
        warden.setPersistent();
        warden.setOnGround(true);  // required to perform path finding immediately
        warden.setGlowing(true);
        EntityUtil.setAttribute(warden, EntityAttributes.GENERIC_STEP_HEIGHT, 2);

        // TODO adjust ai for mini game
        var brain = warden.getBrain();
        brain.setTaskList(Activity.EMERGE, 5, ImmutableList.of(), MemoryModuleType.IS_EMERGING);
        brain.setTaskList(Activity.DIG, 5, ImmutableList.of(), MemoryModuleType.DIG_COOLDOWN);
        brain.resetPossibleActivities();

        EntityNavigation navigation = warden.getNavigation();
        navigation.setRangeMultiplier(8f);

        if (navigation instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanWalkOverFences(true);
            nav.setCanEnterOpenDoors(true);
        }

        if (navigation instanceof ApMobNavigation nav) {
            nav.ap2$patchTrapdoorPathFindingTarget();
        }

        //noinspection DataFlowIssue
        ApEntity apWarden = (ApEntity) warden;

        // fix warden getting stuck on narrow blocks, like open trapdoors on walls / as railings
        apWarden.ap2$patchNarrowMovement();

        // fix continuous jumping when walking by open trapdoors
        apWarden.ap2$patchTrapdoorJumping();

        // adjust PathNodeMaker
        PathNodeMaker nodeMaker = ((EntityNavigationAccessor) navigation).getNodeMaker();

        if (nodeMaker instanceof ApLandPathNodeMaker apPathMaker) {
            apPathMaker.ap2$addPathFindingPredicate(BlockedPathFindingPredicate.getInstance());
            apPathMaker.ap2$addPathFindingPredicate(CollisionPathFindingPredicate.getInstance());
            apPathMaker.ap2$addPathFindingPredicate(new PitPathFindingPredicate(struct));
        }

        world.spawnEntity(warden);
        monsters.add(warden.getUuid());

        targetManager.addMonster(warden);
    }

    private void initAttributes(LivingEntity living) {
        if (!isMonster(living)) return;

        // make sure monsters can track down players everywhere in the map
        EntityUtil.setAttribute(living, EntityAttributes.GENERIC_FOLLOW_RANGE, 2 * mapChunkRadius * 16);
    }

    private boolean isMonster(Entity entity) {
        return world == entity.getWorld() && monsters.contains(entity.getUuid());
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
        for (UUID monsterId : monsters) {
            Entity entity = world.getEntity(monsterId);

            if (entity != null) {
                validatePos(entity);
            }
        }
    }

    private void validatePos(Entity entity) {
        var node = struct.nodeAt(entity.getX(), entity.getY(), entity.getZ());

        if (node == null) {
            teleportToDistantPos(entity);
            return;
        }

        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) {
            teleportToDistantPos(entity);
            return;
        }

        if (oriented.isPitAt(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ())) {
            Vec3d spawn = oriented.spawn();

            if (spawn == null) {
                teleportToDistantPos(entity);
                return;
            }

            teleport(entity, spawn);
        }
    }

    private void teleportToDistantPos(Entity entity) {
        var spawns = mostDistantSpawns();

        if (spawns.isEmpty()) {
            logger.error("Could not find distant position");
            return;
        }

        teleport(entity, spawns.getFirst());
    }

    private void teleport(Entity entity, Vec3d pos) {
        entity.teleport(world, pos.getX(), pos.getY(), pos.getZ(), Set.of(), entity.getYaw(), entity.getPitch());
    }

    private @Nullable Path modifyPathFinding(Entity entity, @Nullable Path path, Set<BlockPos> targets, Function<BlockPos, @Nullable Path> pathFinder) {
        if (!isMonster(entity) || (path != null && path.reachesTarget())) {
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

    private @NotNull List<Passage> findPassagePath(Entity entity, BlockPos target) {
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
}
