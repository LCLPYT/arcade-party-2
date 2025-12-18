package work.lclpnet.ap2.core.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.PigVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Pig.class)
public abstract class PigMixin implements ApVariantHolder<Holder<PigVariant>> {

    @Shadow protected abstract void setVariant(Holder<PigVariant> variant);

    @Override
    public void ap2$setVariant(Holder<PigVariant> variant) {
        setVariant(variant);
    }
}
