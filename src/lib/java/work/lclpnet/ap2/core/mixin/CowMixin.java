package work.lclpnet.ap2.core.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.CowVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Cow.class)
public abstract class CowMixin implements ApVariantHolder<Holder<CowVariant>> {

    @Shadow public abstract void setVariant(Holder<CowVariant> variant);

    @Override
    public void ap2$setVariant(Holder<CowVariant> variant) {
        setVariant(variant);
    }
}
