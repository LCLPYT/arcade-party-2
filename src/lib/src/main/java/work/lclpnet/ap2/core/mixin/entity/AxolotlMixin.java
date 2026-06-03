package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.world.entity.animal.axolotl.Axolotl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Axolotl.class)
public abstract class AxolotlMixin implements ApVariantHolder<Axolotl.Variant> {

    @Shadow protected abstract void setVariant(Axolotl.Variant variant);

    @Override
    public void ap2$setVariant(Axolotl.Variant variant) {
        setVariant(variant);
    }
}
