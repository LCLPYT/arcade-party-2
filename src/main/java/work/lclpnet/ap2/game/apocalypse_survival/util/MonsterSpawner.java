package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Random;

public class MonsterSpawner {

    private static final int
            PARTICLE_TICKS = 12,
            MOB_MIN_TICKS = Ticks.seconds(1),
            MOB_MAX_TICKS = Ticks.seconds(4),
            MOB_LIMIT = 1;
    private final ServerWorld world;
    private final Stage stage;
    private final Random random;
    private int nextParticle = 0;
    private int nextMob;
    private int mobCount = 0;

    public MonsterSpawner(ServerWorld world, Stage stage, Random random) {
        this.world = world;
        this.stage = stage;
        this.random = random;

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

        var followRange = zombie.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);

        if (followRange != null) {
            followRange.setBaseValue(100);
        }

        world.spawnEntity(zombie);
        mobCount++;

        debugPath(zombie);
    }

    private void debugPath(ZombieEntity zombie) {
        zombie.setOnGround(true);

        EntityNavigation navigation = zombie.getNavigation();

        if (navigation instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanWalkOverFences(true);
            nav.setCanEnterOpenDoors(true);
        }

        BlockPos target = new BlockPos(-50, 73, 10);
        var path = navigation.findPathTo(target, 2, 150);

        if (path == null) {
            System.out.println("could not find path");
            return;
        }

        System.out.printf("target: %s reaches: %s distance: %s%n", path.getTarget(), path.reachesTarget(), path.getManhattanDistanceFromTarget());

        for (int i = 0; i < path.getLength(); i++) {
            PathNode node = path.getNode(i);

            var display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            display.setPosition(node.getPos().add(.25, .25, .25));
            DisplayEntityAccess.setBlockState(display, Blocks.LIME_CONCRETE.getDefaultState());
            DisplayEntityAccess.setTransformation(display, new AffineTransformation(new Matrix4f().scale(.5f)));

            world.spawnEntity(display);
        }
    }
}
