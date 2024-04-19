package work.lclpnet.ap2.api.util.collision;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.util.Collider;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface MovementObserver {

    void setRegionEnterListener(BiConsumer<ServerPlayerEntity, Collider> onEnter);

    void setRegionLeaveListener(BiConsumer<ServerPlayerEntity, Collider> onLeave);

    void whenEntering(Collider region, Consumer<ServerPlayerEntity> action);

    void whenLeaving(Collider region, Consumer<ServerPlayerEntity> action);
}
