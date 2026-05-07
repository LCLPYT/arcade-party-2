package work.lclpnet.ap2.impl.util.handler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class VisualCooldown implements Cooldown {

    private final TaskScheduler scheduler;
    private final Map<UUID, TaskHandle> tasks = new HashMap<>();
    private boolean initialized = false;
    @Nullable
    private Consumer<ServerPlayer> onCooldownOver = null;

    public VisualCooldown(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public synchronized void init(HookRegistrar registrar) {
        if (initialized) return;

        initialized = true;

        registrar.registerHook(PlayerConnectionHooks.QUIT, this::resetCooldown);
    }

    @Override
    public void setCooldown(ServerPlayer player, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            resetCooldown(player);
            return;
        }

        enqueueTask(player, cooldownTicks);
    }

    @Override
    public boolean isOnCooldown(ServerPlayer player) {
        synchronized (this) {
            return tasks.containsKey(player.getUUID());
        }
    }

    @Override
    public void resetCooldown(ServerPlayer player) {
        synchronized (this) {
            TaskHandle task = tasks.remove(player.getUUID());

            if (task != null) {
                task.cancel();
            }
        }
    }

    @Override
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

    private void enqueueTask(ServerPlayer player, int cooldownTicks) {
        Task task = new Task(player, cooldownTicks);

        synchronized (this) {
            TaskHandle handle = scheduler.interval(task, 1)
                    .whenComplete(() -> onCooldownOver(player));

            tasks.put(player.getUUID(), handle);
        }
    }

    private void onCooldownOver(ServerPlayer player) {
        resetCooldown(player);

        if (onCooldownOver != null) {
            onCooldownOver.accept(player);
        }

        player.sendOverlayMessage(Component.empty());
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.2f, 2);
    }

    @Override
    public void setOnCooldownOver(@Nullable Consumer<ServerPlayer> onCooldownOver) {
        this.onCooldownOver = onCooldownOver;
    }

    private static class Task implements SchedulerAction {

        private final ServerPlayer player;
        private final float ticks;
        private int remain;
        private int lastSent = -1;

        private Task(ServerPlayer player, int ticks) {
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

            var msg = Component.literal("▌".repeat(boxes)).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("▌".repeat(10 - boxes)).withStyle(ChatFormatting.GRAY));

            player.sendOverlayMessage(msg);
        }
    }
}
