package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.hook.ExplosionAffectedEntitiesCallback;

import java.util.List;

@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {

    @WrapOperation(
            method = "hurtEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> ap2$alterEffectedEntities(ServerLevel instance, Entity entity, AABB box, Operation<List<Entity>> original) {
        List<Entity> entities = original.call(instance, entity, box);
        ServerExplosion self = (ServerExplosion) (Object) this;

        return ExplosionAffectedEntitiesCallback.HOOK.invoker().overrideAffectedEntities(self, entities);
    }
}
