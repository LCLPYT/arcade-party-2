package work.lclpnet.ap2.impl.scene.object;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Mountable;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Unmountable;
import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.simulation.ScenePhysicsElement;
import work.lclpnet.ap2.impl.scene.simulation.SceneRigidBody;
import work.lclpnet.kibu.physics.impl.bullet.collision.body.shape.MinecraftShape;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;
import work.lclpnet.kibu.physics.impl.util.Frame;
import work.lclpnet.kibu.physics.util.BlockPhysics;

import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toMinecraft;

public class PhysicsBlockDisplayObject extends Object3d
        implements ScenePhysicsElement, Mountable, Unmountable, Animatable {

    private final com.jme3.math.Vector3f storedPosition = new com.jme3.math.Vector3f();
    private final Quaternionf storedRotation = new Quaternionf();
    private final Quaternion storedJmeRotation = new Quaternion();
    protected final SceneRigidBody rigidBody;
    private final BlockDisplayObject blockDisplay;
    protected final ServerWorld world;

    public PhysicsBlockDisplayObject(BlockState state, ServerWorld world) {
        this.world = world;

        blockDisplay = new BlockDisplayObject(state);
        blockDisplay.position.set(-0.5f);
        addChild(blockDisplay);

        rigidBody = initRigidBody(world);
    }

    protected SceneRigidBody initRigidBody(ServerWorld world) {
        var rigidBody = new SceneRigidBody(this, world);

        updateRigidBody(rigidBody);

        return rigidBody;
    }

    public void updateRigidBody(SceneRigidBody rigidBody) {
        BlockState state = blockDisplay.getBlockState();

        rigidBody.setMass(BlockPhysics.getMass(state));
        rigidBody.setBuoyancyType(BlockPhysics.getBuoyancyType(state));
        rigidBody.setCollisionShape(this.createShape());
    }

    @Override
    public void mount(MountContext ctx) {
        addPhysics(ctx.world());
    }

    @Override
    public void unmount(MountContext ctx) {
        removePhysics(ctx.world());
    }

    public void addPhysics(ServerWorld world) {
        MinecraftSpace.get(world).addCollisionObject(rigidBody);
    }

    public void removePhysics(ServerWorld world) {
        MinecraftSpace.get(world).removeCollisionObject(rigidBody);
    }

    public void setBlockState(BlockState state) {
        blockDisplay.setBlockState(state);
        updateRigidBody(rigidBody);
    }

    public BlockState getBlockState() {
        return blockDisplay.getBlockState();
    }

    @Override
    @NotNull
    public SceneRigidBody getRigidBody() {
        return rigidBody;
    }

    @Override
    public MinecraftShape.Convex createShape() {
        MinecraftShape.Convex shape = BlockPhysics.getShape(blockDisplay.getBlockState(), world);
        shape.setScale(new Vector3f((float) scale.x, (float) scale.y, (float) scale.z));

        return shape;
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {
        Frame frame = rigidBody.getFrame();
        var pos = frame.getLocation(storedPosition, 1f);

        this.setWorldPosition(pos.x, pos.y, pos.z);
        rotation.set(toMinecraft(frame.getRotation(storedJmeRotation, 0f), storedRotation));
    }

    @Override
    protected void onDetached() {
        removePhysics(world);
    }
}
