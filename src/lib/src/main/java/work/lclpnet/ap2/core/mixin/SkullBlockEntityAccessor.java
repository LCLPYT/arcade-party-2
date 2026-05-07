package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkullBlockEntity.class)
public interface SkullBlockEntityAccessor {

    @Accessor
    void setOwner(@Nullable ResolvableProfile owner);
}
