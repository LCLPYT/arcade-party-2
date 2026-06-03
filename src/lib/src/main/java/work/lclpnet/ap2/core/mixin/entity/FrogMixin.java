package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Frog.class)
public abstract class FrogMixin implements ApVariantHolder<Holder<FrogVariant>> {

    @Shadow protected abstract void setVariant(Holder<FrogVariant> variant);

    @Override
    public void ap2$setVariant(Holder<FrogVariant> variant) {
        setVariant(variant);
    }
}
