package work.lclpnet.ap2.game.maze_scape.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.LookAtMobTask;
import net.minecraft.entity.ai.brain.task.MeleeAttackTask;
import net.minecraft.entity.ai.brain.task.RangedApproachTask;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.ds.Partial;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.hook.BrainCreationCallback;
import work.lclpnet.ap2.core.hook.CobwebSlowCallback;
import work.lclpnet.ap2.core.hook.EntityPathFindingCallback;
import work.lclpnet.ap2.core.hook.LivingEntityAttributeInitCallback;
import work.lclpnet.ap2.core.mixin.EntityNavigationAccessor;
import work.lclpnet.ap2.core.mixin.MobEntityAccessor;
import work.lclpnet.ap2.core.type.*;
import work.lclpnet.ap2.game.apocalypse_survival.util.GoalModifier;
import work.lclpnet.ap2.game.maze_scape.ai.AttackGoal;
import work.lclpnet.ap2.game.maze_scape.ai.MoveToTargetGoal;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.monster.*;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.impl.ai.BlockedPathFindingPredicate;
import work.lclpnet.ap2.impl.ai.CollisionPathFindingPredicate;
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
    private final Map<UUID, MonsterData> monsters = new HashMap<>();

    public MSManager(ServerWorld world, GameMap map, MSStruct struct, Participants participants, Random random, Logger logger, MSDebugController debugController) {
        this.world = world;
        this.struct = struct;
        this.participants = participants;
        this.random = random;
        this.logger = logger;
        this.debugController = debugController;

        targetManager = new MSTargetManager(struct, participants);
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
    }

    public void spawnMobs() {
        // find most distant nodes where the monsters may spawn
        var spawns = spawns();

        if (spawns == null) {
            logger.error("Failed to find spawn points for mobs");
            return;
        }

        if (DEBUG_MOB_SPAWNS) {
            for (Vec3d pos : spawns.source()) {
                debugController.displayMarker(pos.x, pos.y + 0.5, pos.z, Blocks.YELLOW_CONCRETE.getDefaultState(), 0xffff00);
            }
        }

        Partial<MonsterArgs, UUID> args = uuid -> new MonsterArgs(uuid, this, logger, debugController);

//        spawnWarden(spawns.get(), args);
//        spawnSpider(spawns.get(), args);
        spawnEnderman(spawns.get(), args);

        monsters.values().forEach(MonsterData::init);

        targetManager.update();
    }

    public Collection<MonsterData> monsters() {
        return Collections.unmodifiableCollection(monsters.values());
    }

    public void updateMobs() {
        targetManager.update();
    }

    public void tick() {
        for (var data : monsters.values()) {
            data.tick();
        }
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

    private void spawnWarden(Vec3d pos, Partial<MonsterArgs, UUID> args) {
        WardenEntity warden = new WardenEntity(EntityType.WARDEN, world);

        configureMobCommon(pos, warden);

        EntityUtil.setAttribute(warden, EntityAttributes.GENERIC_ATTACK_DAMAGE, 10);

        var brain = warden.getBrain();
        brain.setTaskList(Activity.EMERGE, 5, ImmutableList.of(), MemoryModuleType.IS_EMERGING);
        brain.setTaskList(Activity.DIG, 5, ImmutableList.of(), MemoryModuleType.DIG_COOLDOWN);
        brain.resetPossibleActivities();

        world.spawnEntity(warden);

        UUID uuid = warden.getUuid();
        WardenData data = new WardenData(args.with(uuid));
        monsters.put(uuid, data);

        targetManager.addMonster(data);
    }

    @SuppressWarnings("DataFlowIssue")
    private void spawnSpider(Vec3d pos, Partial<MonsterArgs, UUID> args) {
        SpiderEntity spider = new SpiderEntity(EntityType.SPIDER, world);

        configureMobCommon(pos, spider);

        EntityUtil.setAttribute(spider, EntityAttributes.GENERIC_ATTACK_DAMAGE, 5);

        ((ApSpider) spider).ap2$setCanClimb(false);
        ((ApLivingEntity) spider).ap2$setServerSidedScale(0.64f);  // change spider width to ~0.9

        GoalSelector goalSelector = resetAi(spider).getGoalSelector();

        goalSelector.add(1, new SwimGoal(spider));
        goalSelector.add(3, new PounceAtTargetGoal(spider, 0.4f));
        goalSelector.add(4, new MoveToTargetGoal(spider, 1.0));
        goalSelector.add(4, new AttackGoal(spider));
        goalSelector.add(6, new LookAtEntityGoal(spider, PlayerEntity.class, 8.0f));
        goalSelector.add(6, new LookAroundGoal(spider));

        world.spawnEntity(spider);

        UUID uuid = spider.getUuid();
        SpiderData data = new SpiderData(args.with(uuid), random);
        monsters.put(uuid, data);

        targetManager.addMonster(data);
    }

    private void spawnEnderman(Vec3d pos, Partial<MonsterArgs, UUID> args) {
        EndermanEntity enderman = new EndermanEntity(EntityType.ENDERMAN, world);

        configureMobCommon(pos, enderman);

        EntityUtil.setAttribute(enderman, EntityAttributes.GENERIC_ATTACK_DAMAGE, 20);
        enderman.setSilent(true);

        UUID uuid = enderman.getUuid();
        EndermanData data = new EndermanData(args.with(uuid), struct);

        GoalSelector goalSelector = resetAi(enderman).getGoalSelector();

        goalSelector.add(0, new SwimGoal(enderman));
        goalSelector.add(4, new MoveToTargetGoal(enderman, 1.0, data::targetPos));
        goalSelector.add(4, new AttackGoal(enderman));
        goalSelector.add(6, new LookAroundGoal(enderman));

        world.spawnEntity(enderman);

        monsters.put(uuid, data);

        targetManager.addMonster(data);
    }

    private static @NotNull MobEntityAccessor resetAi(MobEntity mob) {
        var access = (MobEntityAccessor) mob;

        GoalModifier.clear(access.getGoalSelector());
        GoalModifier.clear(access.getTargetSelector());

        return access;
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
        MonsterData data = monsters.get(entity.getUuid());

        if (data == null) return;

        data.onKillAcquired();
    }
}
