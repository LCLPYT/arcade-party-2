package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.apocalypse_survival.goal.RoamGoal;
import work.lclpnet.ap2.game.apocalypse_survival.goal.UnstuckGoal;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.ap2.mixin.MobEntityAccessor;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Random;

public class MonsterSpawner {

    private static final int
            PARTICLE_TICKS = 12,
            MOB_MIN_TICKS = Ticks.seconds(1),
            MOB_MAX_TICKS = Ticks.seconds(3),
            MOB_LIMIT = 150;
    private final ServerWorld world;
    private final Stage stage;
    private final Random random;
    private final TargetManager targetManager;
    private final WeightedList<EntityType<? extends ZombieEntity>> zombieTypes;
    private final WeightedList<EntityType<? extends AbstractSkeletonEntity>> skeletonTypes;
    private final WeightedList<SpawnType> spawnTypes = new WeightedList<>();
    private int timeTicks = 0;
    private int nextParticle = 0;
    private int nextMob;
    private int mobCount = 0;

    public MonsterSpawner(ServerWorld world, Stage stage, Random random, TargetManager targetManager) {
        this.world = world;
        this.stage = stage;
        this.random = random;
        this.targetManager = targetManager;

        zombieTypes = new WeightedList<>();
        zombieTypes.add(EntityType.ZOMBIE, 0.8f);
        zombieTypes.add(EntityType.ZOMBIE_VILLAGER, 0.07f);
        zombieTypes.add(EntityType.HUSK, 0.05f);
        zombieTypes.add(EntityType.ZOMBIFIED_PIGLIN, 0.03f);
        zombieTypes.add(EntityType.DROWNED, 0.05f);

        skeletonTypes = new WeightedList<>();
        skeletonTypes.add(EntityType.SKELETON, 0.8f);
        skeletonTypes.add(EntityType.WITHER_SKELETON, 0.05f);
        skeletonTypes.add(EntityType.BOGGED, 0.05f);
        skeletonTypes.add(EntityType.STRAY, 0.08f);

        scheduleNextMob();
    }

    public void tick() {
        int time = timeTicks++;

        handleTimedEvents(time);

        if (nextParticle-- <= 0) {
            nextParticle = PARTICLE_TICKS;
            spawnParticle();
        }

        if (mobCount < MOB_LIMIT && nextMob-- <= 0) {
            scheduleNextMob();
            spawnMob();
        }
    }

    private void handleTimedEvents(int ticks) {
        switch (ticks) {
            case 0 -> spawnTypes.add(SpawnType.SKELETON, 0.6f);
            case 30 * 20 -> spawnTypes.add(SpawnType.SKELETON, 0.2f);
            default -> {}
        }
    }

    private void scheduleNextMob() {
        this.nextMob = MOB_MIN_TICKS + random.nextInt(MOB_MAX_TICKS - MOB_MIN_TICKS + 1);
    }

    private void spawnParticle() {
        BlockPos center = stage.getCenter();
        int offset = stage.getRadius() / 2;

        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                30, offset, offset, offset, 0.15);
    }

    private void spawnMob() {
        SpawnType spawnType = spawnTypes.getRandomElement(random);

        switch (spawnType) {
            case ZOMBIE -> spawnZombie();
            case SKELETON -> spawnSkeleton();
            case null -> {}
        }
    }

    private void spawnZombie() {
        var zombie = createMob(zombieTypes);

        if (zombie == null) return;

        zombie.setCanBreakDoors(true);

        if (random.nextFloat() < 0.05f)  {
            zombie.setBaby(true);

            EntityAttributeInstance movementSpeed = zombie.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

            if (movementSpeed != null) {
                movementSpeed.setBaseValue(0.18);
            }
        }

        // adjust goals
        var mobAccess = (MobEntityAccessor) zombie;

        GoalSelector goalSelector = mobAccess.getGoalSelector();

        GoalModifier.clear(goalSelector);
        GoalModifier.clear(mobAccess.getTargetSelector());

        goalSelector.add(1, new BreakDoorGoal(zombie, difficulty -> true));
        goalSelector.add(2, new ZombieAttackGoal(zombie, 1.5, false));
        goalSelector.add(7, new RoamGoal(zombie, targetManager, 1.25));
        goalSelector.add(8, new UnstuckGoal(zombie, random));

        // spawn mob
        spawnMobInWorld(zombie);
        targetManager.addZombie(zombie);
    }

    private void spawnSkeleton() {
        var skeleton = createMob(skeletonTypes);

        if (skeleton == null) return;

        // adjust goals
        var mobAccess = (MobEntityAccessor) skeleton;

        GoalSelector goalSelector = mobAccess.getGoalSelector();

        GoalModifier.clear(goalSelector);
        GoalModifier.clear(mobAccess.getTargetSelector());

        goalSelector.add(1, new BreakDoorGoal(skeleton, difficulty -> true));
        goalSelector.add(7, new RoamGoal(skeleton, targetManager, 1.25));
        goalSelector.add(8, new UnstuckGoal(skeleton, random));

        ItemStack stack = skeleton.getStackInHand(ProjectileUtil.getHandPossiblyHolding(skeleton, Items.BOW));

        if (stack.isOf(Items.BOW)) {
            goalSelector.add(4, new BowAttackGoal<>(skeleton, 1.2, 20, 15.0F));
        } else {
            goalSelector.add(4, new MeleeAttackGoal(skeleton, 1.5, false) {
                @Override
                public void start() {
                    super.start();
                    skeleton.setAttacking(true);
                }

                @Override
                public void stop() {
                    super.stop();
                    skeleton.setAttacking(false);
                }
            });
        }

        // spawn mob
        spawnMobInWorld(skeleton);
        targetManager.addSkeleton(skeleton);
    }

    @Nullable
    private <T extends MobEntity> T createMob(WeightedList<EntityType<? extends T>> types) {
        // select random zombie type
        var type = types.getRandomElement(random);

        if (type == null) return null;

        T mob = type.create(world, null, stage.getOrigin(), SpawnReason.COMMAND, false, false);

        if (mob == null) return null;

        configureMob(mob);

        return mob;
    }

    private void configureMob(MobEntity skeleton) {
        BlockPos pos = stage.getOrigin();

        skeleton.setPersistent();
        skeleton.setPosition(Vec3d.ofBottomCenter(pos));
        skeleton.setGlowing(true);  // debug

        // adjust follow range, so that the zombie will follow far players
        var followRange = skeleton.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);

        if (followRange != null) {
            followRange.setBaseValue(100);
        }

        EntityNavigation navigation = skeleton.getNavigation();

        // adjust range multiplier to find longer paths using the A* Algorithm
        navigation.setRangeMultiplier(2.5f);

        if (navigation instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanWalkOverFences(true);
            nav.setCanEnterOpenDoors(true);
        }
    }

    private void spawnMobInWorld(MobEntity mob) {
        world.spawnEntity(mob);
        mobCount++;
    }

    private void debugPath(ZombieEntity zombie) {
        zombie.setOnGround(true);

        BlockPos target = new BlockPos(-50, 73, 10);

        EntityNavigation navigation = zombie.getNavigation();
        var path = navigation.findPathTo(target, 2, 150);

        new PathDebugging(world).displayPath(path, zombie);
    }

    private enum SpawnType {
        ZOMBIE,
        SKELETON,
    }
}
