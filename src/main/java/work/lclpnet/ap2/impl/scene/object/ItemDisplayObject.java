package work.lclpnet.ap2.impl.scene.object;

import lombok.Getter;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Scene;

@Getter
public class ItemDisplayObject extends DisplayEntityObject<DisplayEntity.ItemDisplayEntity> {

    private ItemStack stack;
    private ItemDisplayContext itemDisplayContext = ItemDisplayContext.NONE;

    public ItemDisplayObject(Scene scene, ItemStack stack) {
        super(scene);
        this.stack = stack;
    }

    @Override
    protected DisplayEntity.ItemDisplayEntity createDisplayEntity(MountContext ctx) {
        return new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, ctx.world());
    }

    @Override
    protected void configure(DisplayEntity.ItemDisplayEntity display) {
        super.configure(display);

        display.setItemStack(stack);
        display.setItemDisplayContext(itemDisplayContext);
    }

    @Override
    public ItemDisplayObject deepCopy(Scene scene) {
        var copy = new ItemDisplayObject(scene, stack);

        copy.deepCopy(this);

        return copy;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
        entityRef.optional().ifPresent(display -> display.setItemStack(stack));
    }

    public void setItemDisplayContext(ItemDisplayContext itemDisplayContext) {
        this.itemDisplayContext = itemDisplayContext;
        entityRef.optional().ifPresent(display -> display.setItemDisplayContext(itemDisplayContext));
    }
}
