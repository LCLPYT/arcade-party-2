package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.util.DisplayEntityUtil;
import work.lclpnet.ap2.impl.util.EntityRef;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class ItemDisplayObject extends Object3d implements Spawnable {

    private ItemStack stack;
    private EntityRef<DisplayEntity.ItemDisplayEntity> entityRef = null;

    public ItemDisplayObject(ItemStack stack) {
        this.stack = stack;
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
        var display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        DisplayEntityAccess.setItemStack(display, stack);

        DisplayEntityUtil.applyTransformation(display, matrixWorld);

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

    public ItemStack getStack() {
        return stack;
    }
}
