package work.lclpnet.ap2.game.data

interface IntDataContainer<T, Ref : SubjectRef> : DataContainer<T, Ref>, IntDataSink<T>, ScoreListenerView<T, Int>
