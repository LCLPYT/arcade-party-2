package work.lclpnet.ap2.impl.game.data;

import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.sink.IntDataSink;

public interface IntDataContainer<T, Ref extends SubjectRef> extends
        DataContainer<T, Ref>, IntDataSink<T>, IntScoreEventSource<T> {

}
