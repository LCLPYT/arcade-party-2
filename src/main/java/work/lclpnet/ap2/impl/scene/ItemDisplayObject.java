package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class ItemDisplayObject extends Object3d implements Mountable, Unmountable, Interpolatable {

    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    private ItemStack stack;
    private int interpolationDuration = 0;
    private @NotNull Resolvable<DisplayEntity.ItemDisplayEntity> entityRef = Resolvable.none();

    public ItemDisplayObject(ItemStack stack) {
        this.stack = stack;
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
        var display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, ctx.world());
        DisplayEntityAccess.setItemStack(display, stack);
        DisplayEntityAccess.setInterpolationDuration(display, interpolationDuration);

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
        var display = entityRef.resolve();

        if (display != null) display.discard();

        entityRef = Resolvable.none();
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;

        var display = entityRef.resolve();

        if (display != null) {
            DisplayEntityAccess.setItemStack(display, stack);
        }
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setInterpolationDuration(int interpolationDuration) {
        this.interpolationDuration = interpolationDuration;

        var display = entityRef.resolve();

        if (display != null) {
            DisplayEntityAccess.setInterpolationDuration(display, interpolationDuration);
        }
    }

    public int getInterpolationDuration() {
        return interpolationDuration;
    }
}
