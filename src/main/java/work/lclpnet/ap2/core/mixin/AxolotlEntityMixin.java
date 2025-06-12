package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.AxolotlEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(AxolotlEntity.class)
public abstract class AxolotlEntityMixin implements ApVariantHolder<AxolotlEntity.Variant> {

    @Shadow protected abstract void setVariant(AxolotlEntity.Variant variant);

    @Override
    public void ap2$setVariant(AxolotlEntity.Variant variant) {
        setVariant(variant);
    }
}
