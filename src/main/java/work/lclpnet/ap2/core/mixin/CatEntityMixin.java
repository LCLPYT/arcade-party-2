package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import work.lclpnet.ap2.core.type.ApVariantHolder;

@Mixin(CatEntity.class)
public abstract class CatEntityMixin implements ApVariantHolder<RegistryEntry<CatVariant>> {

    @Shadow protected abstract void setVariant(RegistryEntry<CatVariant> variant);

    @Override
    public void ap2$setVariant(RegistryEntry<CatVariant> variant) {
        setVariant(variant);
    }
}
