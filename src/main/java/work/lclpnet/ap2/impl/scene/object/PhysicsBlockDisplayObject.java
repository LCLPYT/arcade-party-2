package work.lclpnet.ap2.impl.scene.object;

import com.jme3.math.Quaternion;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import org.joml.Quaternionf;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.simulation.ScenePhysicsElement;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.kibu.physics.impl.bullet.collision.body.ElementRigidBody;
import work.lclpnet.kibu.physics.impl.bullet.collision.body.shape.MinecraftShape;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;
import work.lclpnet.kibu.physics.impl.util.Frame;
import work.lclpnet.kibu.physics.util.BlockPhysics;

import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toMinecraft;

public class PhysicsBlockDisplayObject extends BlockDisplayObject implements ScenePhysicsElement, Animatable {

    private final com.jme3.math.Vector3f storedPosition = new com.jme3.math.Vector3f();
    private final Quaternionf storedRotation = new Quaternionf();
    private final Quaternion storedJmeRotation = new Quaternion();
    private final SceneRigidBody rigidBody;
    private final ServerWorld world;

    public PhysicsBlockDisplayObject(BlockState blockState, ServerWorld world) {
        super(blockState);

        this.world = world;

        origin.set(-0.5f);

        rigidBody = initRigidBody(world);
    }

    @Override
    public void mount(MountContext ctx) {
        ServerWorld world = ctx.world();

        super.mount(ctx);

        MinecraftSpace.get(world).addCollisionObject(rigidBody);
    }

    @Override
    public void unmount(MountContext ctx) {
        super.unmount(ctx);

        MinecraftSpace.get(ctx.world()).removeCollisionObject(rigidBody);
    }

    protected SceneRigidBody initRigidBody(ServerWorld world) {
        var rigidBody = new SceneRigidBody(this, world);

        updateRigidBody(rigidBody);

        return rigidBody;
    }

    protected void updateRigidBody(SceneRigidBody rigidBody) {
        BlockState state = getBlockState();

        float mass = BlockPhysics.getMass(state);
        ElementRigidBody.BuoyancyType buoyancyType = BlockPhysics.getBuoyancyType(state);

        rigidBody.setMass(mass);
        rigidBody.setBuoyancyType(buoyancyType);
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);

        updateRigidBody(rigidBody);
    }

    @Override
    public SceneRigidBody getRigidBody() {
        return rigidBody;
    }

    @Override
    public MinecraftShape.Convex createShape() {
        return BlockPhysics.getShape(getBlockState(), world);
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        var display = entityRef.optional().orElse(null);

        if (display == null) return;

        Frame frame = rigidBody.getFrame();
        var pos = frame.getLocation(storedPosition, 1f);

        setWorldPosition(pos.x, pos.y, pos.z);
        rotation.set(toMinecraft(frame.getRotation(storedJmeRotation, 0f), storedRotation));
    }
}
