package work.lclpnet.ap2.impl.util.title;

import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.ArrayList;
import java.util.List;

public class AnimatedTitle {

    private final List<TitleAnimation> animations = new ArrayList<>();
    private TaskHandle taskHandle = null;

    public void add(TitleAnimation animation) {
        synchronized (this) {
            animations.add(animation);

            if (taskHandle != null) {
                animation.begin();
            }
        }
    }

    public void start(TaskScheduler scheduler, int tickRate) {
        synchronized (this) {
            if (taskHandle != null) return;

            animations.forEach(TitleAnimation::begin);

            taskHandle = scheduler.interval(this::tickAll, tickRate);
        }
    }

    public void stop() {
        synchronized (this) {
            if (taskHandle == null) return;

            taskHandle.cancel();
            taskHandle = null;

            animations.forEach(TitleAnimation::destroy);
            animations.clear();
        }
    }

    private void tickAll(RunningTask info) {
        synchronized (this) {
            animations.removeIf(animation -> {
                boolean ended = animation.tick();

                if (ended) {
                    animation.destroy();
                }

                return ended;
            });

            if (!animations.isEmpty()) return;

            // all animations are done
            info.cancel();
            taskHandle = null;
        }
    }
}
