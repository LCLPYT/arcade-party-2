package work.lclpnet.ap2.core.type;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.actor.Actor;

public interface ApMarkerEntity {

    @Nullable Actor ap2$getActor();

    void ap2$setActor(@Nullable Actor actor);
}
