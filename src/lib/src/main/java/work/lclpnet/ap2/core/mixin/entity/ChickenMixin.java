package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Chicken.class)
public abstract class ChickenMixin implements ApVariantHolder<Holder<ChickenVariant>> {

    @Shadow public abstract void setVariant(Holder<ChickenVariant> variant);

    @Override
    public void ap2$setVariant(Holder<ChickenVariant> variant) {
        setVariant(variant);
    }
}
