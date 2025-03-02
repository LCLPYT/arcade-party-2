package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.MarkerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.api.actor.Actor;
import work.lclpnet.ap2.api.actor.ActorData;
import work.lclpnet.ap2.core.type.ApMarkerEntity;

@Mixin(MarkerEntity.class)
public class MarkerEntityMixin implements ApMarkerEntity {

    @Shadow private NbtCompound data;
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

        ActorData<?> actorData = actor.createData();

        if (actorData == null) return;

        actorData.encode(NbtOps.INSTANCE, this.data);
    }
}
