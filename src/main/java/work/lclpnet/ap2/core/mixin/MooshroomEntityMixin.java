package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.MooshroomEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(MooshroomEntity.class)
public abstract class MooshroomEntityMixin implements ApVariantHolder<MooshroomEntity.Variant> {

    @Shadow protected abstract void setVariant(MooshroomEntity.Variant variant);

    @Override
    public void ap2$setVariant(MooshroomEntity.Variant variant) {
        setVariant(variant);
    }
}
