package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.MarkerEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.api.actor.Actor;
import work.lclpnet.ap2.api.actor.ActorManager;
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

    @Inject(
            method = "writeCustomDataToNbt",
            at = @At("HEAD")
    )
    public void ap2$writeActorData(NbtCompound nbt, CallbackInfo ci) {
        if (actor == null) return;

        MarkerEntity self = (MarkerEntity) (Object) this;
        ActorManager.writeActorNbt(self, actor);
    }
}
