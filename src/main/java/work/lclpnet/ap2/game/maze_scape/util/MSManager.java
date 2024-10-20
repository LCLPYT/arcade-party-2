package work.lclpnet.ap2.game.maze_scape.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.hook.BrainCreationCallback;
import work.lclpnet.ap2.hook.LivingEntityAttributeInitCallback;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.ap2.impl.util.world.ChunkPersistence;
import work.lclpnet.ap2.type.WardenBrainHandle;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.function.Supplier;

public class MSManager {

    private static final int MOB_COUNT = 3;
    private final ServerWorld world;
    private final MSStruct struct;
    private final Participants participants;
    private final Logger logger;
    private final MSTargetManager targetManager;
    private final int mapChunkRadius;

    public MSManager(ServerWorld world, GameMap map, MSStruct struct, Participants participants, Logger logger) {
        this.world = world;
        this.struct = struct;
        this.participants = participants;
        this.logger = logger;

        targetManager = new MSTargetManager(struct, participants);
        mapChunkRadius = MSGenerator.getMaxChunkSize(map);
    }

    public void init(MiniGameHandle gameHandle) {
        var hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(LivingEntityAttributeInitCallback.HOOK, this::initAttributes);
        hooks.registerHook(BrainCreationCallback.Warden.HOOK, this::createWardenBrain);

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

    private Set<Graph.Node<Connector3, StructurePiece, OrientedStructurePiece>> playerNodes() {
        Set<Graph.Node<Connector3, StructurePiece, OrientedStructurePiece>> playerNodes = new HashSet<>();

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

        world.spawnEntity(warden);

        ServerPlayerEntity nearest = targetManager.findNearestTarget(warden);

        if (nearest != null) {
            targetManager.assignTarget(warden, nearest);
        }
    }

    private void initAttributes(LivingEntity living) {
        if (!isMonster(living)) return;

        // make sure monsters can track down players everywhere in the map
        EntityUtil.setAttribute(living, EntityAttributes.GENERIC_FOLLOW_RANGE, 2 * mapChunkRadius * 16);
    }

    private boolean isMonster(LivingEntity living) {
        return world == living.getWorld() && (living instanceof WardenEntity);
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
}
