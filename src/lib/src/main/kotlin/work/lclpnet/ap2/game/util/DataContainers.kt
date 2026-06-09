package work.lclpnet.ap2.game.util

import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.data.IntDataContainer
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer

fun <T, Ref : SubjectRef> finaleCompatibleScoreContainer(
    handle: MiniGameHandle,
    refs: SubjectRefFactory<T, Ref>
): IntDataContainer<T, Ref> = when {
    handle.isFinale -> ScoreTimeDataContainer(refs)
    else -> IntScoreDataContainer(refs)
}
