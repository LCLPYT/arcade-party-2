package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import work.lclpnet.ap2.core.type.ApEntity;

@Mixin(Entity.class)
public class EntityMixin implements ApEntity {

    @Unique
    private boolean patchNarrowMovement = false;

    @Override
    public void ap2$patchNarrowMovement() {
        this.patchNarrowMovement = true;
    }

    @Override
    public boolean ap2$isPatchNarrowMovement() {
        return patchNarrowMovement;
    }
}
