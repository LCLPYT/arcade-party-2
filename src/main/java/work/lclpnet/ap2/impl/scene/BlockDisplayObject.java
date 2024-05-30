package work.lclpnet.ap2.impl.scene;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;
import work.lclpnet.ap2.impl.util.EntityRef;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class BlockDisplayObject extends Object3d implements Spawnable, Interpolatable {

    private final BlockState blockState;
    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    private EntityRef<DisplayEntity.BlockDisplayEntity> entityRef = null;

    public BlockDisplayObject(BlockState blockState) {
        this.blockState = blockState;
    }

    @Override
    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        super.updateMatrixWorld(withParent, withChildren);

        if (entityRef != null) {
            var display = entityRef.resolve();

            if (display != null) {
                transformer.applyTransformation(display, matrixWorld);
            }
        }
    }

    @Override
    public void spawn(ServerWorld world) {
        var display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        DisplayEntityAccess.setBlockState(display, blockState);

        transformer.applyTransformation(display, matrixWorld);

        if (!world.spawnEntity(display)) return;

        entityRef = new EntityRef<>(display);
    }

    @Override
    public void updateTickRate(int tickRate) {
        var display = entityRef.resolve();

        if (display != null) {
            DisplayEntityAccess.setInterpolationDuration(display, 1);
        }
    }
}
