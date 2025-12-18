package work.lclpnet.ap2.core.hook;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
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

    boolean onTeleport(Entity owner, ThrownEnderpearl enderPearl, Vec3 pos);
}
