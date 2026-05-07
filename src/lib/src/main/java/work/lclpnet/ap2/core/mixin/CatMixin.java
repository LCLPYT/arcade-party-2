package work.lclpnet.ap2.core.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Cat.class)
public abstract class CatMixin implements ApVariantHolder<Holder<CatVariant>> {

    @Shadow protected abstract void setVariant(Holder<CatVariant> variant);

    @Override
    public void ap2$setVariant(Holder<CatVariant> variant) {
        setVariant(variant);
    }
}
