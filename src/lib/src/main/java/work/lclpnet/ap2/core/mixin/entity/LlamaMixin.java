package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.world.entity.animal.equine.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Llama.class)
public abstract class LlamaMixin implements ApVariantHolder<Llama.Variant> {

    @Shadow protected abstract void setVariant(Llama.Variant variant);

    @Override
    public void ap2$setVariant(Llama.Variant variant) {
        setVariant(variant);
    }
}
