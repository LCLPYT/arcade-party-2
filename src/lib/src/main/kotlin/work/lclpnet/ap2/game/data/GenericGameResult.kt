package work.lclpnet.ap2.game.data

import work.lclpnet.ap2.game.data.type.PlayerRef

interface GenericGameResult<Ref : SubjectRef> {

    val winningPlayers: Set<PlayerRef>

    val winningSubjects: Set<Ref>

    val playerResults: List<Pair<PlayerRef, Int>>

    val subjectResults: List<Pair<Ref, Int>>
}
