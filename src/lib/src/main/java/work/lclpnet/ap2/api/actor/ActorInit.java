package work.lclpnet.ap2.api.actor;

import com.mojang.serialization.Dynamic;
import net.minecraft.server.level.ServerLevel;

public record ActorInit(ServerLevel world, ActorType<?> actorType, Dynamic<?> dataSource) {

}
