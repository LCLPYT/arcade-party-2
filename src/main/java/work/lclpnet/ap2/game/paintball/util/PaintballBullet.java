package work.lclpnet.ap2.game.paintball.util;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.scene.object.PhysicsBlockDisplayObject;
import work.lclpnet.kibu.physics.impl.bullet.collision.body.shape.MinecraftShape;

import java.util.UUID;

public class PaintballBullet extends PhysicsBlockDisplayObject {

    @Getter @Setter
    private UUID owner = null;

    public PaintballBullet(BlockState blockState, ServerWorld world) {
        super(blockState, world);
    }

    @Override
    public MinecraftShape.Convex createShape() {
        MinecraftShape.Convex shape = super.createShape();
        shape.setMargin(0.01f);

        return shape;
    }
}
