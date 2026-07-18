package work.lclpnet.ap2

import net.minecraft.util.Util
import work.lclpnet.ap2.ApConstants.identifier
import work.lclpnet.ap2.api.actor.*
import work.lclpnet.ap2.impl.actor.GravityFieldActor

/**
 * An [ActorProvider] that provides all actor types from arcade-party-2.
 * Instances of this class exist outside the game-instance-scope.
 * An actor manager can receive this instance through a Fabric entrypoint.
 */
class ApActors : ActorProvider {

    override fun provideActors(registrar: ActorRegistrar) {
        registrar.register(GRAVITY_FIELD)
    }

    companion object {
        val GRAVITY_FIELD: ActorType<GravityFieldActor> = ActorType<GravityFieldActor>(
            identifier("gravity_field"),
            ActorFactory.withData(
                GravityFieldActor.Data.CODEC,
                Util.prefix("Parse GravityField data: ") { msg ->
                    ApConstants.logger.error(msg)
                }
            ) { init, data ->
                GravityFieldActor(init, data)
            }
        )
    }
}
