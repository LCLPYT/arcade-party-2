package work.lclpnet.ap2.api.actor;

import net.minecraft.util.Identifier;

public record ActorType<A extends Actor>(Identifier id, ActorFactory<A> factory) {
}
