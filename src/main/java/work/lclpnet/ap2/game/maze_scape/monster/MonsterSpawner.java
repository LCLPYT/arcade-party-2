package work.lclpnet.ap2.game.maze_scape.monster;

import com.google.common.collect.ImmutableList;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.ds.Partial;
import work.lclpnet.ap2.core.mixin.EntityNavigationAccessor;
import work.lclpnet.ap2.core.mixin.MobEntityAccessor;
import work.lclpnet.ap2.core.type.*;
import work.lclpnet.ap2.game.apocalypse_survival.util.GoalModifier;
import work.lclpnet.ap2.game.maze_scape.ai.AttackGoal;
import work.lclpnet.ap2.game.maze_scape.ai.MoveToTargetGoal;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.PitPathFindingPredicate;
import work.lclpnet.ap2.game.maze_scape.util.RandomGenerator;
import work.lclpnet.ap2.impl.ai.BlockedPathFindingPredicate;
import work.lclpnet.ap2.impl.ai.CollisionPathFindingPredicate;
import work.lclpnet.ap2.impl.util.EntityUtil;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class MonsterSpawner {

    private static final boolean DEBUG_SHOW_MOBS = true;

    private final MSManager manager;
    private final Logger logger;
    private final Random random;
    private final ServerWorld world;

    public MonsterSpawner(MSManager manager, Logger logger, Random random) {
        this.manager = manager;
        this.logger = logger;
        this.random = random;

        world = manager.world();
    }

    public void spawn(RandomGenerator<Vec3d> spawns, BiConsumer<UUID, MonsterData> consumer) {
        Partial<MonsterArgs, UUID> args = uuid -> new MonsterArgs(uuid, manager, logger);

        List<MonsterFactory> primary = List.of(this::spawnWarden, this::spawnSpider);
        List<MonsterFactory> secondary = List.of(this::spawnEnderman);

        final int players = manager.participants().count();

        int primaryMobs = max(1, min(primary.size(), players / 2));
        int secondaryMobs = max(1, min(secondary.size(), players / 3));

        for (int i = 0; i < primaryMobs; i++) {
            var factory = primary.get(random.nextInt(primary.size()));
            factory.spawn(spawns.get(), args, consumer);
        }

        for (int i = 0; i < secondaryMobs; i++) {
            var factory = secondary.get(random.nextInt(secondary.size()));
            factory.spawn(spawns.get(), args, consumer);
        }
    }

    private void spawnWarden(Vec3d pos, Partial<MonsterArgs, UUID> args, BiConsumer<UUID, MonsterData> consumer) {
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

        consumer.accept(uuid, data);
    }

    @SuppressWarnings("DataFlowIssue")
    private void spawnSpider(Vec3d pos, Partial<MonsterArgs, UUID> args, BiConsumer<UUID, MonsterData> consumer) {
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

        consumer.accept(uuid, data);
    }

    private void spawnEnderman(Vec3d pos, Partial<MonsterArgs, UUID> args, BiConsumer<UUID, MonsterData> consumer) {
        EndermanEntity enderman = new EndermanEntity(EntityType.ENDERMAN, world);

        configureMobCommon(pos, enderman);

        EntityUtil.setAttribute(enderman, EntityAttributes.GENERIC_ATTACK_DAMAGE, 20);
        enderman.setSilent(true);

        UUID uuid = enderman.getUuid();
        EndermanData data = new EndermanData(args.with(uuid), manager.struct());

        GoalSelector goalSelector = resetAi(enderman).getGoalSelector();

        goalSelector.add(0, new SwimGoal(enderman));
        goalSelector.add(4, new MoveToTargetGoal(enderman, 1.0, data::targetPos));
        goalSelector.add(4, new AttackGoal(enderman));
        goalSelector.add(6, new LookAroundGoal(enderman));

        world.spawnEntity(enderman);

        consumer.accept(uuid, data);
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
        entity.setOnGround(true);  // required to perform path finding immediately

        if (DEBUG_SHOW_MOBS) {
            entity.setGlowing(true);
        }

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
            apPathMaker.ap2$addPathFindingPredicate(new PitPathFindingPredicate(manager.struct()));
        }
    }

    private interface MonsterFactory {
        void spawn(Vec3d pos, Partial<MonsterArgs, UUID> args, BiConsumer<UUID, MonsterData> consumer);
    }
}
