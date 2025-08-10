package work.lclpnet.ap2.impl.scene.simulation;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.kibu.physics.impl.bullet.collision.body.ElementRigidBody;
import work.lclpnet.kibu.physics.impl.bullet.collision.body.shape.MinecraftShape;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;

public class SceneRigidBody extends ElementRigidBody {

    public static final float
            DEFAULT_DRAG_COEFFICIENT = 0.25f,
            DEFAULT_FRICTION = 1.0f,
            DEFAULT_RESTITUTION = 0.5f,
            DEFAULT_MASS = 10f;

    public SceneRigidBody(ScenePhysicsElement element, MinecraftSpace space, MinecraftShape shape, float mass, float dragCoefficient, float friction, float restitution) {
        super(element, space, shape, mass, dragCoefficient, friction, restitution);
    }

    public SceneRigidBody(ScenePhysicsElement element, MinecraftSpace space, MinecraftShape shape) {
        this(element, space, shape, DEFAULT_MASS, DEFAULT_DRAG_COEFFICIENT, DEFAULT_FRICTION, DEFAULT_RESTITUTION);
    }

    public SceneRigidBody(ScenePhysicsElement element, ServerWorld world) {
        this(element, MinecraftSpace.get(world), element.createShape());
    }

    @Override
    public ScenePhysicsElement getElement() {
        return (ScenePhysicsElement) super.getElement();
    }
}
