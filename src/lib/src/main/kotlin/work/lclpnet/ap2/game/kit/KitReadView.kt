package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public interface KitReadView {

    @NotNull Kit getKit(ServerPlayer player);

    boolean hasKitEquipped(ServerPlayer player, Kit kit);
}