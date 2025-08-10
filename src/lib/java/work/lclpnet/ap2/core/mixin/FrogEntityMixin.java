package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.FrogVariant;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(FrogEntity.class)
public abstract class FrogEntityMixin implements ApVariantHolder<RegistryEntry<FrogVariant>> {

    @Shadow protected abstract void setVariant(RegistryEntry<FrogVariant> variant);

    @Override
    public void ap2$setVariant(RegistryEntry<FrogVariant> variant) {
        setVariant(variant);
    }
}
