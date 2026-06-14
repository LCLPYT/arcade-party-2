package work.lclpnet.ap2.game.data

import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.sink.IntDataSink

interface IntDataContainer<T, Ref : SubjectRef> : DataContainer<T, Ref>, IntDataSink<T>, ScoreListenerView<T, Int>
