package work.lclpnet.ap2.api.actor;

import net.minecraft.resources.ResourceLocation;

public record ActorType<A extends Actor>(ResourceLocation id, ActorFactory<A> factory) {
}
