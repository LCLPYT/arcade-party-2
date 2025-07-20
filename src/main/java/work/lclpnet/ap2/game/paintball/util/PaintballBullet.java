package work.lclpnet.ap2.game.paintball.util;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.scene.object.PhysicsBlockDisplayObject;

import java.util.UUID;

public class PaintballBullet extends PhysicsBlockDisplayObject {

    @Getter @Setter
    private UUID owner = null;

    public PaintballBullet(BlockState blockState, ServerWorld world) {
        super(blockState, world);

        scale.set(0.2);

        updateRigidBody(rigidBody);
    }
}
