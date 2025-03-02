package work.lclpnet.ap2.api.actor;

/// An entrypoint for providing custom [Actor] types.
///
/// It is a good practice to keep the [ActorProvider] implementation mostly effectively stateless, so that the actor types
/// can be reused in other projects. That means that no {@link ActorFactory} should depend on externally provided state.
/// This is not a requirement, but a recommendation.
public interface ActorProvider {

    void provideActors(ActorRegistrar registrar);
}
