package work.lclpnet.ap2.impl.scene;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class BlockDisplayObject extends Object3d implements Mountable, Unmountable, Interpolatable {

    @Getter private BlockState blockState;
    @Getter private boolean glowing = false;
    @Getter private int glowColorOverride = -1;
    @Getter private int interpolationDuration = 0;
    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    private @NotNull Resolvable<DisplayEntity.BlockDisplayEntity> entityRef = Resolvable.none();

    public BlockDisplayObject(BlockState blockState) {
        this.blockState = blockState;
    }

    @Override
    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        super.updateMatrixWorld(withParent, withChildren);

        entityRef.optional().ifPresent(display -> transformer.applyTransformation(display, matrixWorld));
    }

    @Override
    public void mount(MountContext ctx) {
        var display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, ctx.world());
        DisplayEntityAccess.setBlockState(display, blockState);
        DisplayEntityAccess.setGlowColorOverride(display, glowColorOverride);
        DisplayEntityAccess.setInterpolationDuration(display, interpolationDuration);

        display.setGlowing(glowing);

        transformer.applyTransformation(display, matrixWorld);

        entityRef = ctx.spawn(display, this);
    }

    @Override
    public void unmount(MountContext ctx) {
        removeDisplay();
    }

    @Override
    public void updateTickRate(int tickRate) {
        setInterpolationDuration(tickRate);
    }

    @Override
    protected void onDetached() {
        removeDisplay();
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

    public void setGlowColorOverride(int glowColorOverride) {
        this.glowColorOverride = glowColorOverride;
        entityRef.optional().ifPresent(display -> display.setGlowColorOverride(glowColorOverride));
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
        entityRef.optional().ifPresent(display -> display.setGlowing(glowing));
    }

    public void setInterpolationDuration(int interpolationDuration) {
        this.interpolationDuration = interpolationDuration;
        entityRef.optional().ifPresent(display -> display.setInterpolationDuration(interpolationDuration));
    }

    private void removeDisplay() {
        entityRef.optional().ifPresent(Entity::discard);
        entityRef = Resolvable.none();
    }
}
