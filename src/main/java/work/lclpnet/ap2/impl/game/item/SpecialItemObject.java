package work.lclpnet.ap2.impl.game.item;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import work.lclpnet.ap2.impl.scene.ItemDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;

public class SpecialItemObject extends Object3d implements Animatable {

    public SpecialItemObject(ItemStack stack) {
        ItemDisplayObject itemDisplay = new ItemDisplayObject(stack);
        itemDisplay.position.set(0, 0.25, 0);
        itemDisplay.setTransformationMode(ModelTransformationMode.GROUND);
        itemDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);

        addChild(itemDisplay);
    }

    @Override
    public void updateAnimation(double dt, AnimationContext ctx) {

    }
}
