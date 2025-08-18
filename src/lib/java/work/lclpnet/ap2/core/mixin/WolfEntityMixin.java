package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariant;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(WolfEntity.class)
public abstract class WolfEntityMixin implements ApVariantHolder<RegistryEntry<WolfVariant>> {

    @Shadow protected abstract void setVariant(RegistryEntry<WolfVariant> variant);

    @Override
    public void ap2$setVariant(RegistryEntry<WolfVariant> variant) {
        setVariant(variant);
    }
}
