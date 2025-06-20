package work.lclpnet.ap2.impl.game.data;

import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;

public class DataContainers {

    public static <T, Ref extends SubjectRef> IntDataContainer<T, Ref> finaleCompatibleScoreContainer(
            MiniGameHandle handle, SubjectRefFactory<T, Ref> refs
    ) {
        return handle.isFinale() ? new ScoreTimeDataContainer<>(refs) : new ScoreDataContainer<>(refs);
    }
}
