package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GbBomb extends Object3d implements Animatable {

    private static final double PARTICLE_PERIOD_SECONDS = 0.2;
    private static final double BEEP_DELAY_SECONDS = 1.3;
    private static final double LAMP_DURATION_SECONDS = 0.45;
    private static final double ROTATION_SPEED = Math.PI / 6;
    private static final double HOVER_SPEED = 0.15;
    private static final double HOVER_AMPLITUDE = 0.15;
    private final ItemStack lampActiveStack = new ItemStack(Items.REDSTONE_TORCH);
    private final ItemStack lampInactiveStack = new ItemStack(Items.LEVER);
    private final List<GbGlowStone> glowStones = new ArrayList<>();
    private final ItemDisplayObject lever;
    private double particleTime = 0;
    private double beepTime = 0;
    private double lampTime = 0;
    private double rotationY = 0;
    private int hoverDirection = 1;
    private double elevation = 0;

    public GbBomb() {
        double v = 0.0625;
        double w = 1.125;

        // lower
        frame(0, -v, -v, 1, v, v);
        frame(0, -v, 1, 1, v, v);
        frame(-v, -v, -v, v, v, w);
        frame(1, -v, -v, v, v, w);

        // upper
        frame(0, 1, -v, 1, v, v);
        frame(0, 1, 1, 1, v, v);
        frame(-v, 1, -v, v, v, w);
        frame(1, 1, -v, v, v, w);

        // sides
        frame(1, 0, -v, v, 1, v);
        frame(1, 0, 1, v, 1, v);
        frame(-v, 0, 1, v, 1, v);
        frame(-v, 0, -v, v, 1, v);

        // glass
        BlockDisplayObject glass = new BlockDisplayObject(Blocks.TINTED_GLASS.getDefaultState());
        glass.position.set(-0.5, -0.5, -0.5);  // glass center to origin
        addChild(glass);

        lever = new ItemDisplayObject(lampInactiveStack);
        lever.position.set(0.875 - 0.5, 1.1875 - 0.5, 0.1875 - 0.5);
        lever.rotation.setAngleAxis(0.5235987755982988, 0, 1, 0);
        lever.scale.set(0.5);

        addChild(lever);
    }

    private void frame(double px, double py, double pz, double sx, double sy, double sz) {
        BlockDisplayObject frame = new BlockDisplayObject(Blocks.RED_CONCRETE.getDefaultState());
        frame.position.set(-0.5, -0.5, -0.5);  // cube center to origin
        frame.scale.set(sx, sy, sz);

        Object3d pivot = new Object3d();
        pivot.position.set(px, py, pz);
        pivot.addChild(frame);

        addChild(pivot);
    }

    public void setGlowStoneAmount(int amount, Random random) {
        glowStones.forEach(this::removeChild);
        glowStones.clear();

        for (int i = 0; i < amount; i++) {
            GbGlowStone glowStone = new GbGlowStone(0.4, 0.4, Math.PI * 0.5);
            glowStone.scale.set(0.2);

            randomRotation(glowStone.rotation, random);

            glowStones.add(glowStone);

            addChild(glowStone);
        }
    }

    private void randomRotation(Quaterniond rotation, Random random) {
        rotation.set(
                random.nextDouble() * 2 - 1,
                random.nextDouble() * 2 - 1,
                random.nextDouble() * 2 - 1,
                random.nextDouble() * 2 - 1
        ).normalize();
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        particleTime += dt;
        beepTime += dt;

        if (particleTime >= PARTICLE_PERIOD_SECONDS) {
            particleTime -= PARTICLE_PERIOD_SECONDS;

            Vector3d fusePos = new Vector3d(0, 0.65, 0);
            matrixWorld.transformPosition(fusePos);

            ctx.world().spawnParticles(ParticleTypes.SMOKE, fusePos.x(), fusePos.y(), fusePos.z(), 0, 0, 1, 0, 0.05);
        }

        if (lever.getStack() == lampActiveStack) {
            lampTime += dt;

            if (lampTime >= LAMP_DURATION_SECONDS) {
                lampTime -= LAMP_DURATION_SECONDS;

                lever.setStack(lampInactiveStack);
            }
        }

        if (beepTime >= BEEP_DELAY_SECONDS) {
            beepTime -= BEEP_DELAY_SECONDS;

            lever.setStack(lampActiveStack);

            Vector3d pos = new Vector3d(0, 0, 0);
            matrixWorld.transformPosition(pos);

            ctx.world().playSound(null, pos.x(), pos.y(), pos.z(), SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 0.75f, 1);
        }

        rotationY = (rotationY + ROTATION_SPEED * dt) % (Math.PI * 2);

        if (rotationY >= Math.PI) {
            rotationY -= Math.PI * 2;
        }

        rotation.setAngleAxis(rotationY, 0, 1, 0);

        double prevElevation = elevation;
        elevation = Math.max(-HOVER_AMPLITUDE, Math.min(HOVER_AMPLITUDE, elevation + hoverDirection * HOVER_SPEED * dt));

        if (elevation <= -HOVER_AMPLITUDE || elevation >= HOVER_AMPLITUDE) {
            hoverDirection *= -1;
        }

        position.setComponent(1, position.get(1) - prevElevation + elevation);
    }
}
