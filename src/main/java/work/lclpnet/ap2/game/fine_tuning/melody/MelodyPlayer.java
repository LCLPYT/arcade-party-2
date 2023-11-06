package work.lclpnet.ap2.game.fine_tuning.melody;

import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;

import java.util.function.IntConsumer;

public class MelodyPlayer implements SchedulerAction {

    private final IntConsumer notePlayer;
    private final int notes;
    private int time = 0;

    public MelodyPlayer(IntConsumer notePlayer, int notes) {
        this.notePlayer = notePlayer;
        this.notes = notes;
    }

    @Override
    public void run(RunningTask info) {
        int t = time++;

        if (t % 10 == 0) {
            int i = t / 10;

            notePlayer.accept(i);

            if (i >= notes - 1) {
                info.cancel();
            }
        }
    }
}
