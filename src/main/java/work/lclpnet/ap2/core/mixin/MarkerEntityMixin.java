package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.MarkerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import work.lclpnet.ap2.api.actor.Actor;
import work.lclpnet.ap2.core.type.ApMarkerEntity;

@Mixin(MarkerEntity.class)
public class MarkerEntityMixin implements ApMarkerEntity {

    @Unique @Nullable
    private Actor actor = null;

    @Override
    public @Nullable Actor ap2$getActor() {
        return actor;
    }

    @Override
    public void ap2$setActor(@Nullable Actor actor) {
        this.actor = actor;
    }
}
