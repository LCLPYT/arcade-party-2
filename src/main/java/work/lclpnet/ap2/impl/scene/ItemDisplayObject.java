package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.util.DisplayEntityUtil;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

public class ItemDisplayObject extends Object3d implements Spawnable {

    private final ItemStack stack;

    public ItemDisplayObject(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void spawn(ServerWorld world) {
        var display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        DisplayEntityAccess.setItemStack(display, stack);

        DisplayEntityUtil.applyTransformation(display, matrixWorld);

        world.spawnEntity(display);
    }
}
