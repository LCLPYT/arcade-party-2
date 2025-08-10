package work.lclpnet.ap2.game.maze_scape.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.LookAtMobTask;
import net.minecraft.entity.ai.brain.task.MeleeAttackTask;
import net.minecraft.entity.ai.brain.task.RangedApproachTask;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.hook.*;
import work.lclpnet.ap2.core.mixin.CreakingBrainAccessor;
import work.lclpnet.ap2.core.mixin.WardenBrainAccessor;
import work.lclpnet.ap2.core.type.ApEntity;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.monster.EndermanData;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterData;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterSpawner;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Math.*;

public class MSManager {

    private static final boolean DEBUG_MOB_SPAWNS = false;

    private final ServerWorld world;
    private final MSStruct struct;
    private final Participants participants;
    private final Random random;
    private final Logger logger;
    private final MSTargetManager targetManager;
    private final int mapChunkRadius;
    private final MSDebugController debugController;
    private final Map<UUID, MonsterData<?>> monsters = new HashMap<>();
    private final MonsterSpawner spawner;

    public MSManager(ServerWorld world, GameMap map, MSStruct struct, Participants participants, Random random, Logger logger, MSDebugController debugController) {
        this.world = world;
        this.struct = struct;
        this.participants = participants;
        this.random = random;
        this.logger = logger;
        this.debugController = debugController;

        targetManager = new MSTargetManager(struct, participants);
        mapChunkRadius = MSGenerator.getMaxChunkSize(map);
        spawner = new MonsterSpawner(this, logger, random);
    }

    public ServerWorld world() {
        return world;
    }

    public MSStruct struct() {
        return struct;
    }

    public MSDebugController debugController() {
        return debugController;
    }

    public void init(MiniGameHandle gameHandle) {
        var hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(LivingEntityAttributeInitCallback.HOOK, this::initAttributes);
        hooks.registerHook(BrainCreationCallback.Warden.HOOK, this::createWardenBrain);
        hooks.registerHook(BrainCreationCallback.Creaking.HOOK, this::createCreakingBrain);
        hooks.registerHook(EntityPathFindingCallback.HOOK, this::modifyPathFinding);
        hooks.registerHook(CobwebSlowCallback.HOOK, this::cancelCobwebSlow);
        hooks.registerHook(EntityAfterMoveCallback.HOOK, this::afterMoveTick);
    }

    public void spawnMobs() {
        // find most distant nodes where the monsters may spawn
        var spawns = spawns();

        if (spawns == null) {
            logger.error("Failed to find spawn points for mobs");
            return;
        }

        if (DEBUG_MOB_SPAWNS) {
            debugController.parent().renderer().ifPresent(renderer -> {
                for (Vec3d pos : spawns.source()) {
                    renderer.marker(pos.x, pos.y + 0.5, pos.z, Blocks.YELLOW_CONCRETE.getDefaultState(), 0xffff00);
                }
            });
        }

        spawner.spawn(spawns, (uuid, data) -> {
            monsters.put(uuid, data);
            targetManager.addMonster(data);
        });

        monsters.values().forEach(MonsterData::init);

        targetManager.update();
    }

    public Collection<MonsterData<?>> monsters() {
        return Collections.unmodifiableCollection(monsters.values());
    }

    public void updateMobs() {
        targetManager.update();
    }

    public void tick() {
        monsters.values().forEach(MonsterData::tick);
    }

    /**
     * Finds a specified amount of spawns for mobs.
     * The spawns are chosen, such so that they are not near any players, if possible.
     * @return A list of spawn positions, is <b>not guaranteed</b> to be of the requested size, if anything is configured wrong.
     */
    public @Nullable RandomGenerator<Vec3d> spawns() {
        var nodes = struct.graph().nodes();
        Object2DoubleMap<Object> minDistances = new Object2DoubleOpenHashMap<>(nodes.size());

        // calculate the min distance to a player for each node
        nodes.forEach(node -> Optional.ofNullable(node.oriented())
                .map(OrientedStructurePiece::spawn)
                .stream()
                .flatMapToDouble(nodeSpawn -> participants.stream()
                        .flatMap(player -> struct.findPath(player.getPos(), nodeSpawn).stream())
                        .mapToDouble(NavPath::length))
                .min()
                .ifPresent(minDist -> minDistances.put(node, minDist)));

        // sort by calculated min dist descending
        nodes.sort(Comparator.comparingDouble(node -> minDistances.getOrDefault(node, Double.MIN_VALUE)).reversed());

        // select elements randomly out of certain % of most distant elements
        double threshold = 0.3;
        int thresholdIdx = max(0, min(nodes.size() - 1, (int) floor(nodes.size() * (threshold))));

        var mostDistant = nodes.subList(0, thresholdIdx).stream()
                .map(Node::oriented)
                .filter(Objects::nonNull)
                .map(OrientedStructurePiece::spawn)
                .filter(Objects::nonNull)
                .toList();

        if (mostDistant.isEmpty()) {
            return null;
        }

        return new RandomGenerator<>(mostDistant, random);
    }

    private void initAttributes(LivingEntity entity) {
        if (entity.getWorld() != world || !isMonsterType(entity)) return;

        // make sure monsters can track down players everywhere in the map
        EntityUtil.setAttribute(entity, EntityAttributes.FOLLOW_RANGE, 2 * mapChunkRadius * 16);
    }

    private static boolean isMonsterType(LivingEntity entity) {
        return entity instanceof WardenEntity
                || entity instanceof SpiderEntity
                || entity instanceof EndermanEntity
                || entity instanceof CreakingEntity;
    }

    private @Nullable Brain<WardenEntity> createWardenBrain(WardenEntity warden, Supplier<Brain<WardenEntity>> brainSupplier) {
        if (warden.getWorld() != world) return null;

        var brain = brainSupplier.get();

        // adjusted activities from WardenBrain::create
        WardenBrainAccessor.invokeAddCoreActivities(brain);  // don't add emerge and dig activities
        WardenBrainAccessor.invokeAddIdleActivities(brain);
        WardenBrainAccessor.invokeAddRoarActivities(brain);
        WardenBrainAccessor.invokeAddInvestigateActivities(brain);
        WardenBrainAccessor.invokeAddSniffActivities(brain);

        // custom fight activity
        brain.setTaskList(
                Activity.FIGHT,
                10,
                ImmutableList.of(
                        LookAtMobTask.create(entity -> isTargeting(warden, entity), (float)warden.getAttributeValue(EntityAttributes.FOLLOW_RANGE)),
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

    private @Nullable Brain<CreakingEntity> createCreakingBrain(CreakingEntity creaking, Supplier<Brain<CreakingEntity>> brainSupplier) {
        if (creaking.getWorld() != world) return null;

        var brain = brainSupplier.get();

        // adjusted activities from CreakingBrain::create
        CreakingBrainAccessor.invokeAddCoreTasks(brain);

        // custom fight activity
        brain.setTaskList(
                Activity.FIGHT,
                10,
                ImmutableList.of(
                        RangedApproachTask.create(1.0F),
                        MeleeAttackTask.create(CreakingEntity::isUnrooted, 40)
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
        var navPath = struct.findPath(entity.getPos(), target.toBottomCenterPos());

        if (navPath.isEmpty()) {
            return null;
        }

        // try to find partial path towards the passage on half the way
        List<Passage> passages = navPath.get().path();

        final int size = passages.size();
        int i = size - 1;

        while (i >= 0 && i < size) {
            Passage passage = passages.get(i);
            Path partial = pathFinder.apply(passage.pos());

            if (partial != null && partial.reachesTarget() && partial.getLength() > 2) {
                return partial;
            }

            // no path could be found, try to half the way again
            i = ((i + 1) / 2) - 1;
        }

        return null;
    }

    private boolean cancelCobwebSlow(Entity entity, BlockPos blockPos) {
        return entity.getWorld() == world && monsters.containsKey(entity.getUuid());
    }

    public Participants participants() {
        return participants;
    }

    public void onKillAcquired(Entity entity) {
        MonsterData<?> data = monsters.get(entity.getUuid());

        if (data == null) return;

        data.onKillAcquired();
    }

    private void afterMoveTick(MobEntity mob) {
        if (mob.getWorld() != world || !(monsters.get(mob.getUuid()) instanceof EndermanData data)) return;

        // make the enderman always face the target player while fleeing
        LivingEntity target = mob.getTarget();
        var handle = (ApEntity) mob;

        if (!data.isFleeing() || target == null) {
            handle.ap2$setUseMovementYaw(false);
            return;
        }

        // store original yaw for movement calculation
        handle.ap2$setUseMovementYaw(true);
        handle.ap2$setMovementYaw(mob.getYaw());

        // but look at the target player all the time
        mob.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
    }
}
