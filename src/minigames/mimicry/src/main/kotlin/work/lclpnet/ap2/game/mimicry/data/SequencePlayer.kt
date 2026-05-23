package work.lclpnet.ap2.game.mimicry.data

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.SchedulerAction
import work.lclpnet.kibu.scheduler.api.TaskScheduler

class SequencePlayer(
    private val manager: MimicryManager,
    private val scheduler: TaskScheduler,
    private val world: ServerLevel
) : SchedulerAction {

    private var t = 0
    private var periodTicks = 12

    fun play(): work.lclpnet.kibu.scheduler.api.TaskHandle {
        t = 0
        return scheduler.interval(periodTicks, this)
    }

    fun setPeriodTicks(periodTicks: Int) {
        this.periodTicks = maxOf(4, periodTicks)
    }

    override fun run(info: RunningTask) {
        val time = t++
        val i = time / 2

        val done = i >= manager.sequenceLength()

        if (done || time % 2 == 1) {
            if (done) {
                info.cancel()
            }

            manager.eachParticipant { _, room -> room.resetActiveButton(world) }

            return
        }

        val button = manager.sequenceItem(i)
        val pitch = manager.getButtonPitch(button)

        manager.eachParticipant { player, room ->
            val pos: BlockPos = room.buttonPos(button)

            SoundHelper.playSound(player, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS,
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), 0.5f, pitch)

            room.setButtonActive(button, world)
        }
    }
}
