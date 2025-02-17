package work.lclpnet.ap2.impl.scene;

import lombok.Getter;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;

@Getter
public class ItemDisplayObject extends DisplayEntityObject<DisplayEntity.ItemDisplayEntity> {

    private ItemStack stack;
    private ModelTransformationMode transformationMode = ModelTransformationMode.NONE;

    public ItemDisplayObject(ItemStack stack) {
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
        display.setTransformationMode(transformationMode);
    }

    @Override
    public ItemDisplayObject deepCopy() {
        var copy = new ItemDisplayObject(stack);

        copy.deepCopy(this);

        return copy;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
        entityRef.optional().ifPresent(display -> display.setItemStack(stack));
    }

    public void setTransformationMode(ModelTransformationMode transformationMode) {
        this.transformationMode = transformationMode;
        entityRef.optional().ifPresent(display -> display.setTransformationMode(transformationMode));
    }
}
