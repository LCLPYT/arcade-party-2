package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.Resolvable;

public interface MountContext {

    ServerWorld world();

    <T extends Entity> Resolvable<@Nullable T> spawn(@Nullable T entity, Object3d origin);

    <T extends Entity> void remove(@Nullable T entity, Object3d origin);
}
