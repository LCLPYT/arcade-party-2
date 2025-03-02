package work.lclpnet.ap2.api.actor;

import com.mojang.serialization.Dynamic;
import net.minecraft.server.world.ServerWorld;

public interface ActorInit {

    ServerWorld world();

    Dynamic<?> dataSource();
}
