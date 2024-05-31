package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.ItemDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.Animation;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GbBomb extends Object3d implements Animatable {

    private final Runnable onYielded;
    private final ItemStack lampActiveStack = new ItemStack(Items.REDSTONE_TORCH);
    private final ItemStack lampInactiveStack = new ItemStack(Items.LEVER);
    private final List<GbGlowStone> glowStones = new ArrayList<>();
    private final ItemDisplayObject lever;
    private final Animation idleAnimation = new Animation(new IdleAnimation()).running();
    @Nullable
    private Animation yieldAnimation = null;
    private int requiredYieldCount = 0;
    private int yielded = 0;

    public GbBomb(Runnable onYielded) {
        this.onYielded = onYielded;

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

        double incline = Math.PI / amount;

        for (int i = 0; i < amount; i++) {
            double initialAngle = random.nextDouble() * Math.PI * 2 - Math.PI;
            double orbitSpeed = Math.PI * (random.nextDouble() * 0.4 + 0.55);
            double rotationSpeed = Math.PI * (random.nextDouble() * 0.3 + 1.1);

            GbGlowStone glowStone = new GbGlowStone(initialAngle, i * incline, orbitSpeed, rotationSpeed);
            glowStone.scale.set(0.2);

            glowStones.add(glowStone);

            addChild(glowStone);
        }
    }

    public int getGlowStoneAmount() {
        return glowStones.size();
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        idleAnimation.updateAnimation(dt, ctx);

        if (yieldAnimation != null) {
            yieldAnimation.updateAnimation(dt, ctx);
        }
    }

    @Override
    protected void onChildRemoved(Object3d child) {
        if (!(child instanceof GbGlowStone)) return;

        yielded++;

        if (yielded == requiredYieldCount) {
            onYielded.run();
        }
    }

    public void yieldGlowStone(GbManager manager, GbAnchor anchor) {
        idleAnimation.stop();

        yieldAnimation = new Animation(new YieldAnimation(manager, anchor));
        yieldAnimation.start();
    }

    private class IdleAnimation implements Animatable {

        private static final double PARTICLE_PERIOD_SECONDS = 0.2;
        private static final double BEEP_DELAY_SECONDS = 1.3;
        private static final double LAMP_DURATION_SECONDS = 0.45;
        private static final double ROTATION_SPEED = Math.PI / 6;
        private static final double HOVER_SPEED = 0.15;
        private static final double HOVER_AMPLITUDE = 0.15;

        private double particleTime = 0;
        private double beepTime = 0;
        private double lampTime = 0;
        private double rotationY = 0;
        private int hoverDirection = 1;
        private double elevation = 0;

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

    private class YieldAnimation implements Animatable {

        private static final double YIELD_DELAY_SECONDS = 0.75;

        private final GbManager manager;
        private final GbAnchor anchor;
        private double yieldDelay = YIELD_DELAY_SECONDS;

        private YieldAnimation(GbManager manager, GbAnchor anchor) {
            this.manager = manager;
            this.anchor = anchor;

            requiredYieldCount = glowStones.size();
        }

        @Override
        public void updateAnimation(double dt, AnimationContext ctx) {
            if (glowStones.isEmpty()) return;

            yieldDelay -= dt;

            if (yieldDelay > 0) return;

            yieldDelay += YIELD_DELAY_SECONDS;

            GbGlowStone glowStone = glowStones.removeFirst();
            glowStone.yieldInto(anchor, manager);

            Vector3d pos = worldPosition();

            ctx.world().playSound(null, pos.x(), pos.y(), pos.z(), SoundEvents.BLOCK_CRAFTER_CRAFT, SoundCategory.HOSTILE, 1f, 1.25f);
        }
    }
}
