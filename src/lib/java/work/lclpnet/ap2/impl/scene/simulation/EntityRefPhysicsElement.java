package work.lclpnet.ap2.impl.scene.simulation;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.EntityRef;
import work.lclpnet.kibu.physics.api.PhysicsElement;
import work.lclpnet.kibu.physics.impl.bullet.collision.body.shape.MinecraftShape;

public class EntityRefPhysicsElement implements PhysicsElement<EntityRef<?>> {

    private final EntityRef<?> ref;
    private final EntityRefRigidBody rigidBody;

    public EntityRefPhysicsElement(EntityRef<?> ref) {
        this.ref = ref;
        rigidBody = new EntityRefRigidBody(this, ref.require().getWorld());
    }

    @Override
    public @NotNull EntityRefRigidBody getRigidBody() {
        return rigidBody;
    }

    @Override
    public MinecraftShape.Convex createShape() {
        Entity entity = ref.require();
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());

        final Box box = dimensions.getBoxAt(Vec3d.ZERO);

        return MinecraftShape.convex(box);
    }

    @Override
    public EntityRef<?> cast() {
        return ref;
    }
}
