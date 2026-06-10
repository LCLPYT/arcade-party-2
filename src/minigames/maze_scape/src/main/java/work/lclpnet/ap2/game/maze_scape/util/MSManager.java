package work.lclpnet.ap2.game.maze_scape.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.core.hook.*;
import work.lclpnet.ap2.core.mixin.entity.CreakingAiAccessor;
import work.lclpnet.ap2.core.mixin.entity.WardenAiAccessor;
import work.lclpnet.ap2.core.type.ApEntity;
import work.lclpnet.ap2.ext.mc.EntityExtensionsKt;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.monster.CreakingData;
import work.lclpnet.ap2.game.maze_scape.monster.EndermanData;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterData;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterSpawner;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.player.Participants;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.access.entity.EntityUtil;
import work.lclpnet.kibu.hook.util.PendingResult;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Math.clamp;
import static java.lang.Math.floor;

public class MSManager {

    private static final boolean DEBUG_MOB_SPAWNS = false;

    private final ServerLevel world;
    private final MSStruct struct;
    private final Participants participants;
    private final Random random;
    private final Logger logger;
    private final MSTargetManager targetManager;
    private final MSDebugController debugController;
    private final Map<UUID, MonsterData<?>> monsters = new HashMap<>();
    private final MonsterSpawner spawner;

    public MSManager(ServerLevel world, GameMap map, MSStruct struct, Participants participants, Random random, Logger logger, MSDebugController debugController) {
        this.world = world;
        this.struct = struct;
        this.participants = participants;
        this.random = random;
        this.logger = logger;
        this.debugController = debugController;

        targetManager = new MSTargetManager(struct, participants);
        spawner = new MonsterSpawner(this, logger, random);
    }

    public ServerLevel world() {
        return world;
    }

    public MSStruct struct() {
        return struct;
    }

    public MSDebugController debugController() {
        return debugController;
    }

    public void init(MiniGameHandle gameHandle) {
        var hooks = gameHandle.getHooks();

        LivingEntityAttributeInitCallback.HOOK.registerWith(hooks, this::initAttributes);
        BrainCreationCallback.Warden.HOOK.registerWith(hooks, this::createWardenBrain);
        BrainCreationCallback.Creaking.HOOK.registerWith(hooks, this::createCreakingBrain);
        EntityPathFindingCallback.HOOK.registerWith(hooks, this::modifyPathFinding);
        CobwebSlowCallback.HOOK.registerWith(hooks, this::cancelCobwebSlow);
        EntityAfterMoveCallback.HOOK.registerWith(hooks, this::afterMoveTick);
        CreakingLookedAtCheckCallback.HOOK.registerWith(hooks, this::isCreakingBeingLookedAt);
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
                for (Vec3 pos : spawns.source()) {
                    renderer.marker(pos.x, pos.y + 0.5, pos.z, Blocks.YELLOW_CONCRETE.defaultBlockState(), 0xffff00);
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
    public @Nullable RandomGenerator<Vec3> spawns() {
        var nodes = struct.graph().nodes();
        Object2DoubleMap<Object> minDistances = new Object2DoubleOpenHashMap<>(nodes.size());

        // calculate the min distance to a player for each node
        nodes.forEach(node -> Optional.ofNullable(node.oriented())
                .map(OrientedStructurePiece::spawn)
                .stream()
                .flatMapToDouble(nodeSpawn -> participants.stream()
                        .flatMap(player -> struct.findPath(player.position(), nodeSpawn).stream())
                        .mapToDouble(NavPath::length))
                .min()
                .ifPresent(minDist -> minDistances.put(node, minDist)));

        // sort by calculated min dist descending
        nodes.sort(Comparator.comparingDouble(node -> minDistances.getOrDefault(node, Double.MIN_VALUE)).reversed());

        // select elements randomly out of certain % of most distant elements
        double threshold = 0.3;
        int thresholdIdx = clamp((int) floor(nodes.size() * (threshold)), 0, nodes.size() - 1);

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
        if (entity.level() != world || !isMonsterType(entity)) return;

        EntityUtil.setAttribute(entity, Attributes.FOLLOW_RANGE, 80);
    }

    private static boolean isMonsterType(LivingEntity entity) {
        return entity instanceof Warden
                || entity instanceof Spider
                || entity instanceof EnderMan
                || entity instanceof Creaking;
    }

    private @Nullable Brain<Warden> createWardenBrain(Warden warden, Supplier<Brain<Warden>> brainSupplier) {
        if (warden.level() != world) return null;

        var brain = brainSupplier.get();

        // adjusted activities from net.minecraft.world.entity.monster.warden.WardenAi#getActivities
        EntityExtensionsKt.addActivity(brain, WardenAiAccessor.invokeInitCoreActivity());  // don't add emerge and dig activities
        EntityExtensionsKt.addActivity(brain, WardenAiAccessor.invokeInitIdleActivity());
        EntityExtensionsKt.addActivity(brain, WardenAiAccessor.invokeInitRoarActivity());
        EntityExtensionsKt.addActivity(brain, WardenAiAccessor.invokeInitInvestigateActivity());
        EntityExtensionsKt.addActivity(brain, WardenAiAccessor.invokeInitSniffingActivity());

        // adjusted activity from net.minecraft.world.entity.monster.warden.WardenAi.initFightActivity
        EntityExtensionsKt.addActivity(brain, ActivityData.create(
                Activity.FIGHT,
                10,
                                       ImmutableList.of(
                        SetEntityLookTarget.create(entity -> isTargeting(warden, entity), (float)warden.getAttributeValue(Attributes.FOLLOW_RANGE)),
                        SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.2F),
                        MeleeAttack.create(18)
                ),
                MemoryModuleType.ATTACK_TARGET
        ));

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();

        return brain;
    }

    private @Nullable Brain<Creaking> createCreakingBrain(Creaking creaking, Supplier<Brain<Creaking>> brainSupplier) {
        if (creaking.level() != world) return null;

        var brain = brainSupplier.get();

        // adjusted activities from CreakingAi::create
        EntityExtensionsKt.addActivity(brain, CreakingAiAccessor.invokeInitCoreActivity());

        // custom fight activity
        EntityExtensionsKt.addActivity(brain, ActivityData.create(
                Activity.FIGHT,
                10,
                ImmutableList.of(
                        SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F),
                        MeleeAttack.create(Creaking::canMove, 40)
                ),
                MemoryModuleType.ATTACK_TARGET
        ));

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();

        return brain;
    }

    private static boolean isTargeting(Warden warden, LivingEntity entity) {
        return warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(x -> x == entity).isPresent();
    }

    private @Nullable Path modifyPathFinding(Entity entity, @Nullable Path path, Set<BlockPos> targets, Function<BlockPos, @Nullable Path> pathFinder) {
        if (!monsters.containsKey(entity.getUUID()) || (path != null && path.canReach())) {
            return path;
        }

        // try to find the shortest partial path, as the no real target could directly be reached
        return targets.stream()
                .map(target -> findPartialPath(entity, target, pathFinder))
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(Path::getNodeCount))
                .orElse(path);
    }

    private @Nullable Path findPartialPath(Entity entity, BlockPos target, Function<BlockPos, Path> pathFinder) {
        var navPath = struct.findPath(entity.position(), target.getBottomCenter());

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

            if (partial != null && partial.canReach() && partial.getNodeCount() > 2) {
                return partial;
            }

            // no path could be found, try to half the way again
            i = ((i + 1) / 2) - 1;
        }

        return null;
    }

    private boolean cancelCobwebSlow(Entity entity, BlockPos blockPos) {
        return entity.level() == world && monsters.containsKey(entity.getUUID());
    }

    public Participants participants() {
        return participants;
    }

    public void onKillAcquired(Entity entity) {
        MonsterData<?> data = monsters.get(entity.getUUID());

        if (data == null) return;

        data.onKillAcquired();
    }

    private void afterMoveTick(Mob mob) {
        if (mob.level() != world || !(monsters.get(mob.getUUID()) instanceof EndermanData data)) return;

        // make the enderman always face the target player while fleeing
        LivingEntity target = mob.getTarget();
        var handle = (ApEntity) mob;

        if (!data.isFleeing() || target == null) {
            handle.ap2$setUseMovementYaw(false);
            return;
        }

        // store original yaw for movement calculation
        handle.ap2$setUseMovementYaw(true);
        handle.ap2$setMovementYaw(mob.getYRot());

        // but look at the target player all the time
        mob.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
    }

    private PendingResult<Boolean> isCreakingBeingLookedAt(Creaking creaking) {
        if (creaking.level() != world || !(monsters.get(creaking.getUUID()) instanceof CreakingData data)) {
            return PendingResult.pass();
        }

        return PendingResult.of(data.isBeingLookedAt(creaking));
    }
}
