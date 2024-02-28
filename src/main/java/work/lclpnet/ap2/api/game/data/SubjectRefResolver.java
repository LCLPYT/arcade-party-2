package work.lclpnet.ap2.api.game.data;

import org.jetbrains.annotations.Nullable;

public interface SubjectRefResolver<T, Ref extends SubjectRef> {

    @Nullable
    T resolve(Ref ref);
}
