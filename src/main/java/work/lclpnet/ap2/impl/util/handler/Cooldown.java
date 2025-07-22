package work.lclpnet.ap2.impl.util.handler;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface Cooldown {

    void setCooldown(ServerPlayerEntity player, int cooldownTicks);

    boolean isOnCooldown(ServerPlayerEntity player);

    void resetCooldown(ServerPlayerEntity player);

    void resetAll();

    void setOnCooldownOver(@Nullable Consumer<ServerPlayerEntity> onCooldownOver);
}
