package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Wolf.class)
public abstract class WolfMixin implements ApVariantHolder<Holder<WolfVariant>> {

    @Shadow protected abstract void setVariant(Holder<WolfVariant> variant);

    @Override
    public void ap2$setVariant(Holder<WolfVariant> variant) {
        setVariant(variant);
    }
}
