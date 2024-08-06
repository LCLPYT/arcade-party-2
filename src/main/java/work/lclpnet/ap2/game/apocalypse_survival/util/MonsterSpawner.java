package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.BreakDoorGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.game.apocalypse_survival.goal.RoamGoal;
import work.lclpnet.ap2.game.apocalypse_survival.goal.UnstuckGoal;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.ap2.mixin.MobEntityAccessor;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Random;

public class MonsterSpawner {

    private static final int
            PARTICLE_TICKS = 12,
            MOB_MIN_TICKS = Ticks.seconds(1),
            MOB_MAX_TICKS = Ticks.seconds(4),
            MOB_LIMIT = 100;
    private final ServerWorld world;
    private final Stage stage;
    private final Random random;
    private final TargetManager targetManager;
    private int nextParticle = 0;
    private int nextMob;
    private int mobCount = 0;

    public MonsterSpawner(ServerWorld world, Stage stage, Random random, TargetManager targetManager) {
        this.world = world;
        this.stage = stage;
        this.random = random;
        this.targetManager = targetManager;

        scheduleNextMob();
    }

    public void tick() {
        if (nextParticle-- <= 0) {
            nextParticle = PARTICLE_TICKS;
            spawnParticle();
        }

        if (mobCount < MOB_LIMIT && nextMob-- <= 0) {
            scheduleNextMob();
            spawnMob();
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
        BlockPos pos = stage.getOrigin();

        ZombieEntity zombie = new ZombieEntity(EntityType.ZOMBIE, world);
        zombie.setPersistent();
        zombie.setPosition(Vec3d.ofBottomCenter(pos));
        zombie.setCanBreakDoors(true);
        zombie.setGlowing(true);  // debug

        // adjust follow range, so that the zombie will follow far players
        var followRange = zombie.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);

        if (followRange != null) {
            followRange.setBaseValue(100);
        }

        EntityNavigation navigation = zombie.getNavigation();

        // adjust range multiplier to find longer paths using the A* Algorithm
        navigation.setRangeMultiplier(2.5f);

        if (navigation instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanWalkOverFences(true);
            nav.setCanEnterOpenDoors(true);
        }

        adjustGoals(zombie);

        world.spawnEntity(zombie);
        mobCount++;

        targetManager.addZombie(zombie);

//        debugPath(zombie);
    }

    private void adjustGoals(ZombieEntity zombie) {
        var mobAccess = (MobEntityAccessor) zombie;

        GoalSelector goalSelector = mobAccess.getGoalSelector();
        GoalSelector targetSelector = mobAccess.getTargetSelector();

        GoalModifier.clear(goalSelector);
        GoalModifier.clear(targetSelector);

        goalSelector.add(1, new BreakDoorGoal(zombie, difficulty -> true));
        goalSelector.add(2, new ZombieAttackGoal(zombie, 1.5, false));
        goalSelector.add(7, new RoamGoal(zombie, targetManager, 1.25));
        goalSelector.add(8, new UnstuckGoal(zombie, random));
    }

    private void debugPath(ZombieEntity zombie) {
        zombie.setOnGround(true);

        BlockPos target = new BlockPos(-50, 73, 10);

        EntityNavigation navigation = zombie.getNavigation();
        var path = navigation.findPathTo(target, 2, 150);

        new PathDebugging(world).displayPath(path, zombie);
    }
}
