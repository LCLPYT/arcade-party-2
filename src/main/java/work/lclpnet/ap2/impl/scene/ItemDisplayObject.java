package work.lclpnet.ap2.impl.scene;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;

public class ItemDisplayObject extends Object3d implements Mountable, Unmountable, Interpolatable {

    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    @Getter private ItemStack stack;
    @Getter private int interpolationDuration = 0;
    @Getter private ModelTransformationMode transformationMode = ModelTransformationMode.NONE;
    @Getter private DisplayEntity.BillboardMode billboardMode = DisplayEntity.BillboardMode.FIXED;

    private @NotNull Resolvable<DisplayEntity.@Nullable ItemDisplayEntity> entityRef = Resolvable.none();

    public ItemDisplayObject(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        super.updateMatrixWorld(withParent, withChildren);

        entityRef.optional().ifPresent(display -> transformer.applyTransformation(display, matrixWorld));
    }

    @Override
    public void mount(MountContext ctx) {
        var display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, ctx.world());
        display.setItemStack(stack);
        display.setInterpolationDuration(interpolationDuration);
        display.setTransformationMode(transformationMode);
        display.setBillboardMode(billboardMode);

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
    public ItemDisplayObject deepCopy() {
        var copy = new ItemDisplayObject(stack);

        copy.deepCopy(this);

        return copy;
    }

    private void removeDisplay() {
        entityRef.optional().ifPresent(Entity::discard);
        entityRef = Resolvable.none();
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
        entityRef.optional().ifPresent(display -> display.setItemStack(stack));
    }

    public void setInterpolationDuration(int interpolationDuration) {
        this.interpolationDuration = interpolationDuration;
        entityRef.optional().ifPresent(display -> display.setInterpolationDuration(interpolationDuration));
    }

    public void setTransformationMode(ModelTransformationMode transformationMode) {
        this.transformationMode = transformationMode;
        entityRef.optional().ifPresent(display -> display.setTransformationMode(transformationMode));
    }

    public void setBillboardMode(DisplayEntity.BillboardMode billboardMode) {
        this.billboardMode = billboardMode;
        entityRef.optional().ifPresent(display -> display.setBillboardMode(billboardMode));
    }
}
