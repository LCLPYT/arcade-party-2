package work.lclpnet.ap2.game.mimicry.data;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public class SequencePlayer implements SchedulerAction {

    private final MimicryManager manager;
    private final TaskScheduler scheduler;
    private final ServerWorld world;
    private int t;
    private int periodTicks = 12;

    public SequencePlayer(MimicryManager manager, TaskScheduler scheduler, ServerWorld world) {
        this.manager = manager;
        this.scheduler = scheduler;
        this.world = world;
    }

    public TaskHandle play() {
        t = 0;

        return scheduler.interval(this, periodTicks);
    }

    public void setPeriodTicks(int periodTicks) {
        this.periodTicks = Math.max(5, periodTicks);
    }

    @Override
    public void run(RunningTask info) {
        int time = t++;
        int i = time / 2;

        boolean done = i >= manager.sequenceLength();

        if (done || time % 2 == 1) {
            if (done) {
                info.cancel();
            }

            manager.eachParticipant((player, room) -> room.resetActiveButton(world));

            return;
        }

        int button = manager.sequenceItem(i);
        float pitch = manager.getButtonPitch(button);

        manager.eachParticipant((player, room) -> {
            BlockPos pos = room.buttonPos(button);

            SoundHelper.playSound(player, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, pos.getX(), pos.getY(), pos.getZ(), 0.5f, pitch);

            room.setButtonActive(button, world);
        });
    }
}
