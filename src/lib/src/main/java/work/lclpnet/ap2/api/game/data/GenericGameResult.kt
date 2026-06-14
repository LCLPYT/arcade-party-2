package work.lclpnet.ap2.api.game.data

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import work.lclpnet.ap2.game.data.type.PlayerRef

interface GenericGameResult<Ref : SubjectRef> {

    val winningPlayers: Set<PlayerRef>

    val winningSubjects: Set<Ref>

    val playerResults: List<ObjectIntPair<PlayerRef>>

    val subjectResults: List<ObjectIntPair<Ref>>
}
