package work.lclpnet.ap2.impl.util.handler;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface Cooldown {

    void setCooldown(ServerPlayer player, int cooldownTicks);

    boolean isOnCooldown(ServerPlayer player);

    void resetCooldown(ServerPlayer player);

    void resetAll();

    void setOnCooldownOver(@Nullable Consumer<ServerPlayer> onCooldownOver);
}
