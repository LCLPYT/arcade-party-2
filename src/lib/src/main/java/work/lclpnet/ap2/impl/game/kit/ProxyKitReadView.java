package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProxyKitReadView implements KitReadView {

    private @Nullable KitReadView delegate = null;

    public void inject(@Nullable KitReadView delegate) {
        this.delegate = delegate;
    }

    @Override
    public @NotNull Kit getKit(ServerPlayer player) {
        return delegate().getKit(player);
    }

    @Override
    public boolean hasKitEquipped(ServerPlayer player, Kit kit) {
        return delegate().hasKitEquipped(player, kit);
    }

    private @NotNull KitReadView delegate() {
        KitReadView delegate = this.delegate;

        if (delegate == null) {
            throw new IllegalArgumentException("No delegate set");
        }

        return delegate;
    }
}
