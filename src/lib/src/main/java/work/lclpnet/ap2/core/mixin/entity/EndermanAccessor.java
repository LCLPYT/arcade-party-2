package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Enderman.class)
public interface EndermanAccessor {

    @Accessor("DATA_CREEPY")
    static EntityDataAccessor<Boolean> DATA_CREEPY() {
        throw new AssertionError();
    }

    @Accessor("DATA_STARED_AT")
    static EntityDataAccessor<Boolean> DATA_STARED_AT() {
        throw new AssertionError();
    }
}
