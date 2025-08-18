package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.PigVariant;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(PigEntity.class)
public abstract class PigEntityMixin implements ApVariantHolder<RegistryEntry<PigVariant>> {

    @Shadow protected abstract void setVariant(RegistryEntry<PigVariant> variant);

    @Override
    public void ap2$setVariant(RegistryEntry<PigVariant> variant) {
        setVariant(variant);
    }
}
