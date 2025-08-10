package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.ParrotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(ParrotEntity.class)
public abstract class ParrotEntityMixin implements ApVariantHolder<ParrotEntity.Variant> {

    @Shadow protected abstract void setVariant(ParrotEntity.Variant variant);

    @Override
    public void ap2$setVariant(ParrotEntity.Variant variant) {
        setVariant(variant);
    }
}
