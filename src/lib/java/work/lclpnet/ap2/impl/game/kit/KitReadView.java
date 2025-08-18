package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

public interface KitReadView {

    @NotNull Kit getKit(ServerPlayerEntity player);

    boolean hasKitEquipped(ServerPlayerEntity player, Kit kit);
}