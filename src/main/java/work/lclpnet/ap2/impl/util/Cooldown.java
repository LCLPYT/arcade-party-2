package work.lclpnet.ap2.impl.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class Cooldown {

    private final TaskScheduler scheduler;
    private final Map<UUID, TaskHandle> tasks = new HashMap<>();
    @Nullable
    private Consumer<ServerPlayerEntity> onCooldownOver = null;

    public Cooldown(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void init(HookRegistrar registrar) {
        registrar.registerHook(PlayerConnectionHooks.QUIT, this::resetCooldown);
    }

    public void setCooldown(ServerPlayerEntity player, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            resetCooldown(player);
            return;
        }

        enqueueTask(player, cooldownTicks);
    }

    public boolean isOnCooldown(ServerPlayerEntity player) {
        synchronized (this) {
            return tasks.containsKey(player.getUuid());
        }
    }

    public void resetCooldown(ServerPlayerEntity player) {
        synchronized (this) {
            TaskHandle task = tasks.remove(player.getUuid());

            if (task != null) {
                task.cancel();
            }
        }
    }

    public void resetAll() {
        synchronized (this) {
            var iterator = tasks.values().iterator();

            while (iterator.hasNext()) {
                TaskHandle task = iterator.next();
                iterator.remove();

                task.cancel();
            }
        }
    }

    private void enqueueTask(ServerPlayerEntity player, int cooldownTicks) {
        Task task = new Task(player, cooldownTicks);

        synchronized (this) {
            TaskHandle handle = scheduler.interval(task, 1)
                    .whenComplete(() -> onCooldownOver(player));

            tasks.put(player.getUuid(), handle);
        }
    }

    private void onCooldownOver(ServerPlayerEntity player) {
        player.sendMessage(Text.empty(), true);
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.2f, 2);

        resetCooldown(player);

        if (onCooldownOver != null) {
            onCooldownOver.accept(player);
        }
    }

    public void setOnCooldownOver(@Nullable Consumer<ServerPlayerEntity> onCooldownOver) {
        this.onCooldownOver = onCooldownOver;
    }

    private static class Task implements SchedulerAction {

        private final ServerPlayerEntity player;
        private final float ticks;
        private int remain;
        private int lastSent = -1;

        private Task(ServerPlayerEntity player, int ticks) {
            if (ticks <= 0) throw new IllegalArgumentException("Ticks must be positive");

            this.player = player;
            this.ticks = ticks;  // use one tick less to show the last symbol briefly
            this.remain = ticks;
        }

        @Override
        public void run(RunningTask info) {
            if (player.isRemoved() || remain <= 0) {
                info.cancel();
                return;
            }

            final int t = remain--;

            float progress = Math.min(1, Math.max(0, 1 - t / ticks));

            int boxes = Math.round(progress * 10);

            if (boxes == lastSent) return;

            lastSent = boxes;

            var msg = Text.literal("▌".repeat(boxes)).formatted(Formatting.GREEN)
                    .append(Text.literal("▌".repeat(10 - boxes)).formatted(Formatting.GRAY));

            player.sendMessage(msg, true);
        }
    }
}
