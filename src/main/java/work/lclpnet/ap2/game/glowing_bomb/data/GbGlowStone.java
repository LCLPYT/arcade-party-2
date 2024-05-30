package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.Animation;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.simulation.SimpleGravityGradient;
import work.lclpnet.ap2.impl.scene.simulation.StateVector;
import work.lclpnet.ap2.impl.scene.simulation.solver.NumericalSolver;
import work.lclpnet.ap2.impl.scene.simulation.solver.RungeKuttaSolver;

import java.util.function.Consumer;

import static java.lang.Math.PI;

public class GbGlowStone extends Object3d implements Animatable {

    private final Consumer<GbGlowStone> detach;
    private final Animation orbitAnimation;
    @Nullable
    private Animation yieldAnimation = null;

    public GbGlowStone(Consumer<GbGlowStone> detach, double initialAngle, double incline, double orbitSpeed, double rotationSpeed) {
        this.detach = detach;
        orbitAnimation = new Animation(new OrbitAnimation(initialAngle, incline, orbitSpeed, rotationSpeed)).running();

        BlockDisplayObject glowStone = new BlockDisplayObject(Blocks.GLOWSTONE.getDefaultState());
        glowStone.position.set(-0.5, -0.5, -0.5);  // center to origin

        addChild(glowStone);
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        orbitAnimation.updateAnimation(dt, ctx);

        if (yieldAnimation != null) {
            yieldAnimation.updateAnimation(dt, ctx);
        }
    }

    public void yieldInto(GbAnchor anchor, GbManager manager) {
        orbitAnimation.stop();

        yieldAnimation = new Animation(new YieldAnimation(anchor, manager));
        yieldAnimation.start();
    }

    private class OrbitAnimation implements Animatable {

        private static final double TWO_PI = PI * 2;
        private static final double ROTATION_AXIS = 1 / Math.sqrt(3);

        private final double orbitAngularVelocity;
        private final double rotationAngularVelocity;
        private final Quaterniond orbitRotation = new Quaterniond();
        private double orbitAngle;
        private double rotationAngle;

        private OrbitAnimation(double initialAngle, double incline, double orbitAngularVelocity, double rotationAngularVelocity) {
            this.orbitAngle = initialAngle;
            this.rotationAngle = initialAngle;
            this.orbitAngularVelocity = orbitAngularVelocity;
            this.rotationAngularVelocity = rotationAngularVelocity;

            orbitRotation.setAngleAxis(incline, 1, 0, 0);
        }

        @Override
        public void updateAnimation(double dt, AnimationContext ctx) {
            // update angle
            orbitAngle = (orbitAngle + orbitAngularVelocity * dt) % TWO_PI;
            rotationAngle = (rotationAngle + rotationAngularVelocity * dt) % TWO_PI;

            if (orbitAngle >= PI) {
                orbitAngle -= TWO_PI;
            }

            if (rotationAngle >= PI) {
                rotationAngle -= TWO_PI;
            }

            double x = 0.3 * Math.cos(orbitAngle);
            double z = 0.3 * Math.sin(orbitAngle);

            position.set(x, 0, z);
            orbitRotation.transform(position);

            rotation.setAngleAxis(rotationAngle, ROTATION_AXIS, ROTATION_AXIS, ROTATION_AXIS);
        }
    }

    private class YieldAnimation implements Animatable {

        private static final double GRAVITY_ACCELERATION = 9.81;  // m/s^2
        private final GbManager manager;
        private final GbAnchor anchor;
        private final Vector3d targetPos;
        private final StateVector state;
        private final NumericalSolver solver;
        private final SimpleGravityGradient gradient;
        private boolean complete = false;

        public YieldAnimation(GbAnchor anchor, GbManager manager) {
            this.manager = manager;
            this.anchor = anchor;

            this.gradient = new SimpleGravityGradient(GRAVITY_ACCELERATION);
            this.solver = RungeKuttaSolver.INSTANCE;

            Vec3d anchorPos = anchor.pos();
            targetPos = new Vector3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);

            Vector3d worldPos = worldPosition();
            Vector3d velocity = gradient.getLaunchVelocity(worldPos, targetPos, 2);

            this.state = new StateVector(new Vector3d[] { worldPos, velocity });
        }

        @Override
        public void updateAnimation(double dt, AnimationContext ctx) {
            double distanceSq = worldPosition().distanceSquared(targetPos);

            if (distanceSq <= 0.25) {
                if (!complete) {
                    complete = true;
                    manager.addCharge(anchor);
                    detach.accept(GbGlowStone.this);
                }

                return;
            }

            solver.solve(state, dt, gradient);

            setWorldPosition(state.getVector3(0));
        }
    }
}
