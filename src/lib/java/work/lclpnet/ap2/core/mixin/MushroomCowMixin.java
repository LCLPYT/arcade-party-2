package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.animal.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(MushroomCow.class)
public abstract class MushroomCowMixin implements ApVariantHolder<MushroomCow.Variant> {

    @Shadow protected abstract void setVariant(MushroomCow.Variant variant);

    @Override
    public void ap2$setVariant(MushroomCow.Variant variant) {
        setVariant(variant);
    }
}
