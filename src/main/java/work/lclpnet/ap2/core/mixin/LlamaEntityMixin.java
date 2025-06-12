package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.LlamaEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(LlamaEntity.class)
public abstract class LlamaEntityMixin implements ApVariantHolder<LlamaEntity.Variant> {

    @Shadow protected abstract void setVariant(LlamaEntity.Variant variant);

    @Override
    public void ap2$setVariant(LlamaEntity.Variant variant) {
        setVariant(variant);
    }
}
