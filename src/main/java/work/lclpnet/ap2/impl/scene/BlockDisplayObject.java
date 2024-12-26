package work.lclpnet.ap2.impl.scene;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class BlockDisplayObject extends Object3d implements Mountable, Unmountable, Interpolatable {

    private BlockState blockState;
    private boolean glowing = false;
    private int glowColorOverride = -1;
    private int interpolationDuration = 0;
    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    private @NotNull Resolvable<DisplayEntity.BlockDisplayEntity> entityRef = Resolvable.none();

    public BlockDisplayObject(BlockState blockState) {
        this.blockState = blockState;
    }

    @Override
    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        super.updateMatrixWorld(withParent, withChildren);

        var display = entityRef.resolve();

        if (display != null) {
            transformer.applyTransformation(display, matrixWorld);
        }
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

        var entity = entityRef.resolve();

        if (entity != null) {
            DisplayEntityAccess.setBlockState(entity, state);
        }
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public void setGlowColorOverride(int glowColorOverride) {
        this.glowColorOverride = glowColorOverride;

        var entity = entityRef.resolve();

        if (entity != null) {
            DisplayEntityAccess.setGlowColorOverride(entity, glowColorOverride);
        }
    }

    public int getGlowColorOverride() {
        return glowColorOverride;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;

        var entity = entityRef.resolve();

        if (entity != null) {
            entity.setGlowing(glowing);
        }
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setInterpolationDuration(int interpolationDuration) {
        this.interpolationDuration = interpolationDuration;

        var display = entityRef.resolve();

        if (display != null) {
            DisplayEntityAccess.setInterpolationDuration(display, 1);
        }
    }

    public int getInterpolationDuration() {
        return interpolationDuration;
    }

    private void removeDisplay() {
        var display = entityRef.resolve();

        if (display != null) display.discard();

        entityRef = Resolvable.none();
    }
}
