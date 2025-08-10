package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.FoxEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(FoxEntity.class)
public abstract class FoxEntityMixin implements ApVariantHolder<FoxEntity.Variant> {

    @Shadow protected abstract void setVariant(FoxEntity.Variant variant);

    @Override
    public void ap2$setVariant(FoxEntity.Variant variant) {
        setVariant(variant);
    }
}
