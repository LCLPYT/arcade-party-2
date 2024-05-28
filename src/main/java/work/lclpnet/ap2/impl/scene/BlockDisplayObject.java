package work.lclpnet.ap2.impl.scene;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.util.DisplayEntityUtil;
import work.lclpnet.ap2.impl.util.EntityRef;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class BlockDisplayObject extends Object3d implements Spawnable {

    private final BlockState blockState;
    private EntityRef<DisplayEntity.BlockDisplayEntity> entityRef = null;

    public BlockDisplayObject(BlockState blockState) {
        this.blockState = blockState;
    }

    @Override
    public void updateMatrixWorld() {
        super.updateMatrixWorld();

        if (entityRef != null) {
            var display = entityRef.resolve();

            if (display != null) {
                DisplayEntityUtil.applyTransformation(display, matrixWorld);
            }
        }
    }

    @Override
    public void spawn(ServerWorld world) {
        var display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        DisplayEntityAccess.setBlockState(display, blockState);

        DisplayEntityUtil.applyTransformation(display, matrixWorld);

        if (!world.spawnEntity(display)) return;

        entityRef = new EntityRef<>(display);
    }
}
