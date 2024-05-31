package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.scene.animation.Interpolatable;
import work.lclpnet.ap2.impl.util.DisplayEntityTransformer;
import work.lclpnet.ap2.impl.util.EntityRef;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class ItemDisplayObject extends Object3d implements Mountable, Unmountable, Interpolatable {

    private final DisplayEntityTransformer transformer = new DisplayEntityTransformer();
    private ItemStack stack;
    private EntityRef<DisplayEntity.ItemDisplayEntity> entityRef = null;

    public ItemDisplayObject(ItemStack stack) {
        this.stack = stack;
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
    public void mount(ServerWorld world) {
        var display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        DisplayEntityAccess.setItemStack(display, stack);

        transformer.applyTransformation(display, matrixWorld);

        if (!(world.spawnEntity(display))) return;

        entityRef = new EntityRef<>(display);
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;

        var display = entityRef.resolve();

        if (display != null) {
            DisplayEntityAccess.setItemStack(display, stack);
        }
    }

    @Override
    public void unmount(ServerWorld world) {
        removeDisplay();
    }

    @Override
    public void updateTickRate(int tickRate) {
        var display = entityRef.resolve();

        if (display != null) {
            DisplayEntityAccess.setInterpolationDuration(display, 1);
        }
    }

    @Override
    protected void onDetached() {
        removeDisplay();
    }

    private void removeDisplay() {
        if (entityRef == null) return;

        var display = entityRef.resolve();

        if (display != null) display.discard();

        entityRef = null;
    }

    public ItemStack getStack() {
        return stack;
    }
}
