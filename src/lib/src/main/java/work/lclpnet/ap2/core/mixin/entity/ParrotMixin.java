package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.world.entity.animal.parrot.Parrot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Parrot.class)
public abstract class ParrotMixin implements ApVariantHolder<Parrot.Variant> {

    @Shadow protected abstract void setVariant(Parrot.Variant variant);

    @Override
    public void ap2$setVariant(Parrot.Variant variant) {
        setVariant(variant);
    }
}
