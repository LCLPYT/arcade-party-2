package work.lclpnet.ap2.impl.scene.object;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import work.lclpnet.ap2.impl.scene.MountContext;

@Getter
public class BlockDisplayObject extends DisplayEntityObject<DisplayEntity.BlockDisplayEntity> {

    private BlockState blockState;

    public BlockDisplayObject(BlockState blockState) {
        this.blockState = blockState;
    }

    @Override
    protected DisplayEntity.BlockDisplayEntity createDisplayEntity(MountContext ctx) {
        return new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, ctx.world());
    }

    @Override
    protected void configure(DisplayEntity.BlockDisplayEntity display) {
        super.configure(display);

        display.setBlockState(blockState);
    }

    @Override
    public BlockDisplayObject deepCopy() {
        var copy = new BlockDisplayObject(blockState);

        copy.deepCopy(this);

        return copy;
    }

    public void setBlockState(BlockState state) {
        this.blockState = state;
        entityRef.optional().ifPresent(display -> display.setBlockState(state));
    }
}
