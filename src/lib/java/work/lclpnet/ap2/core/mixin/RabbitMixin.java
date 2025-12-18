package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.animal.Rabbit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(Rabbit.class)
public abstract class RabbitMixin implements ApVariantHolder<Rabbit.Variant> {

    @Shadow protected abstract void setVariant(Rabbit.Variant variant);

    @Override
    public void ap2$setVariant(Rabbit.Variant variant) {
        setVariant(variant);
    }
}
