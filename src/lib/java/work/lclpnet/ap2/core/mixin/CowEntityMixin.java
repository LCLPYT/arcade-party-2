package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.CowVariant;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(CowEntity.class)
public abstract class CowEntityMixin implements ApVariantHolder<RegistryEntry<CowVariant>> {

    @Shadow public abstract void setVariant(RegistryEntry<CowVariant> variant);

    @Override
    public void ap2$setVariant(RegistryEntry<CowVariant> variant) {
        setVariant(variant);
    }
}
