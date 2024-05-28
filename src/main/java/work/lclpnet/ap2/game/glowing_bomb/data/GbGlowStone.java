package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.Blocks;
import org.joml.Quaterniond;
import work.lclpnet.ap2.impl.scene.Animatable;
import work.lclpnet.ap2.impl.scene.AnimationContext;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;

import static java.lang.Math.PI;

public class GbGlowStone extends Object3d implements Animatable {

    private static final double TWO_PI = PI * 2;
    private static final double ROTATION_AXIS = 1 / Math.sqrt(3);
    private final double orbitAngularVelocity;
    private final double rotationAngularVelocity;
    private final Quaterniond orbitRotation = new Quaterniond();
    private double orbitAngle;
    private double rotationAngle;

    public GbGlowStone(double initialAngle, double incline, double orbitAngularVelocity, double rotationAngularVelocity) {
        orbitAngle = initialAngle;
        rotationAngle = initialAngle;
        this.orbitAngularVelocity = orbitAngularVelocity;
        this.rotationAngularVelocity = rotationAngularVelocity;

        orbitRotation.setAngleAxis(incline, 1, 0, 0);

        BlockDisplayObject glowStone = new BlockDisplayObject(Blocks.GLOWSTONE.getDefaultState());
        glowStone.position.set(-0.5, -0.5, -0.5);  // center to origin

        addChild(glowStone);
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
