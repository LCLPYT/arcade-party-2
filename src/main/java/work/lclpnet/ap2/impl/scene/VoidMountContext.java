package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.Resolvable;

public final class VoidMountContext implements MountContext {

    public static final VoidMountContext INSTANCE = new VoidMountContext();

    private VoidMountContext() {}

    @Override
    public ServerWorld world() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Entity> Resolvable<@Nullable T> spawn(@Nullable T entity, Object3d origin) {
        return Resolvable.none();
    }

    @Override
    public <T extends Entity> void remove(@Nullable T entity, Object3d origin) {}
}
