package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Fox.class)
public abstract class FoxMixin implements ApVariantHolder<Fox.Variant> {

    @Shadow protected abstract void setVariant(Fox.Variant variant);

    @Override
    public void ap2$setVariant(Fox.Variant variant) {
        setVariant(variant);
    }
}
