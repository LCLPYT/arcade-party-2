package work.lclpnet.ap2.impl.scene.simulation;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.kibu.physics.api.PhysicsElement;

public interface ScenePhysicsElement extends PhysicsElement<Object3d> {

    @Override
    @Nullable SceneRigidBody getRigidBody();
}
