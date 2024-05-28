package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.Blocks;
import work.lclpnet.ap2.impl.scene.Animatable;
import work.lclpnet.ap2.impl.scene.AnimationContext;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;

import static java.lang.Math.PI;

public class GbGlowStone extends BlockDisplayObject implements Animatable {

    public static final double TWO_PI = PI * 2;
    private final double orbitEllipseX, orbitEllipseZ;
    private final double angularVelocity;
    private double angle = 0;

    public GbGlowStone(double orbitEllipseX, double orbitEllipseZ, double angularVelocity) {
        super(Blocks.GLOWSTONE.getDefaultState());
        this.orbitEllipseX = orbitEllipseX;
        this.orbitEllipseZ = orbitEllipseZ;
        this.angularVelocity = angularVelocity;
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        // update angle
        angle = (angle + angularVelocity * dt) % TWO_PI;

        if (angle >= PI) {
            angle -= TWO_PI;
        }

        if (angle < PI) {
            angle += TWO_PI;
        }

        double x = orbitEllipseX * Math.cos(angle);
        double z = orbitEllipseZ * Math.sin(angle);

        position.set(x, 0, z);

        rotation.transform(position);

        position.add(0.5, 0.5, 0.5);
    }
}
