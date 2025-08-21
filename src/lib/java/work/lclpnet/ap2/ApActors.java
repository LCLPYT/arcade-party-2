package work.lclpnet.ap2;

import net.minecraft.util.Util;
import work.lclpnet.ap2.api.actor.ActorProvider;
import work.lclpnet.ap2.api.actor.ActorRegistrar;
import work.lclpnet.ap2.api.actor.ActorType;
import work.lclpnet.ap2.impl.actor.GravityFieldActor;

import static work.lclpnet.ap2.ApConstants.identifier;
import static work.lclpnet.ap2.ApConstants.logger;
import static work.lclpnet.ap2.api.actor.ActorFactory.withData;

/**
 * An {@link ActorProvider} that provides all actor types from arcade-party-2.
 * Instances of this class exist outside the game-instance-scope.
 * An actor manager can receive this instance through a Fabric entrypoint.
 */
public class ApActors implements ActorProvider {

    public static final ActorType<GravityFieldActor> GRAVITY_FIELD = new ActorType<>(identifier("gravity_field"), withData(GravityFieldActor.Data.CODEC,
            Util.addPrefix("Parse GravityField data: ", logger::error),
            GravityFieldActor::new));

    @Override
    public void provideActors(ActorRegistrar registrar) {
        registrar.register(GRAVITY_FIELD);
    }
}
