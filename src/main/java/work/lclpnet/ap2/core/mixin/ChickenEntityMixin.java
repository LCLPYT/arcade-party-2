package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.ChickenVariant;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(ChickenEntity.class)
public abstract class ChickenEntityMixin implements ApVariantHolder<RegistryEntry<ChickenVariant>> {

    @Shadow public abstract void setVariant(RegistryEntry<ChickenVariant> variant);

    @Override
    public void ap2$setVariant(RegistryEntry<ChickenVariant> variant) {
        setVariant(variant);
    }
}
