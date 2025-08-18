package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface EnderPearlTeleportCallback {

    Hook<EnderPearlTeleportCallback> HOOK = HookFactory.createArrayBacked(EnderPearlTeleportCallback.class, hooks -> (owner, enderPearl, pos) -> {
        boolean cancel = false;

        for (var hook : hooks) {
            if (hook.onTeleport(owner, enderPearl, pos)) {
                cancel = true;
            }
        }

        return cancel;
    });

    boolean onTeleport(Entity owner, EnderPearlEntity enderPearl, Vec3d pos);
}
