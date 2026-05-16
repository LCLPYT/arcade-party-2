package work.lclpnet.ap2.game.fine_tuning.melody

import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.SchedulerAction

class PlayMelodyTask(
    private val notePlayer: (Int) -> Unit,
    private val notes: Int
) : SchedulerAction {

    private var time = 0

    override fun run(info: RunningTask) {
        val t = time++

        if (t % 10 == 0) {
            val i = t / 10
            notePlayer(i)
            if (i >= notes - 1) info.cancel()
        }
    }
}
