package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.DataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.player.Participants
import kotlin.time.Duration

class TaskEnv(
    val players: Participants,
) {

    fun timer(labelKey: String, duration: Duration, onEnd: () -> Unit) {

    }

    fun complete(data: DataContainer<ServerPlayer, PlayerRef>) {

    }
}

interface Task {

    val id: String

    fun begin(env: TaskEnv)
}