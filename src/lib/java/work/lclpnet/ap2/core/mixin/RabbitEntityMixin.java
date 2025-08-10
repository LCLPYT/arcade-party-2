package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.RabbitEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(RabbitEntity.class)
public abstract class RabbitEntityMixin implements ApVariantHolder<RabbitEntity.Variant> {

    @Shadow protected abstract void setVariant(RabbitEntity.Variant variant);

    @Override
    public void ap2$setVariant(RabbitEntity.Variant variant) {
        setVariant(variant);
    }
}
