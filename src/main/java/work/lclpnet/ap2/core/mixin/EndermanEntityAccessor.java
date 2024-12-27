package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EndermanEntity.class)
public interface EndermanEntityAccessor {

    @Accessor("ANGRY")
    static TrackedData<Boolean> ANGRY() {
        throw new AssertionError();
    }

    @Accessor("PROVOKED")
    static TrackedData<Boolean> PROVOKED() {
        throw new AssertionError();
    }
}
