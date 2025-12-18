package work.lclpnet.ap2.impl.util.handler;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SimpleCooldown implements Cooldown {

    private final Object2IntMap<UUID> cooldown = new Object2IntOpenHashMap<>();
    private final PlayerList playerManager;
    private final List<UUID> completions = new ArrayList<>();
    private TaskHandle task = null;
    private @Nullable Consumer<ServerPlayer> onCooldownOver = null;

    public SimpleCooldown(PlayerList playerManager) {
        this.playerManager = playerManager;
    }

    public synchronized void init(TaskScheduler scheduler) {
        if (task != null) return;
        
        task = scheduler.interval(this::tick, 1);
    }
    
    private void tick() {
        synchronized (this) {
            var it = cooldown.object2IntEntrySet().iterator();

            while (it.hasNext()) {
                var entry = it.next();
                int remainingTicks = entry.getIntValue();

                if (remainingTicks <= 0) {
                    completions.add(entry.getKey());
                    it.remove();
                    continue;
                }

                entry.setValue(remainingTicks - 1);
            }
        }

        completions.forEach(this::onComplete);
        completions.clear();
    }

    private void onComplete(UUID uuid) {
        var onCooldownOver = this.onCooldownOver;

        if (onCooldownOver == null) return;

        ServerPlayer player = playerManager.getPlayer(uuid);

        if (player != null) {
            onCooldownOver.accept(player);
        }
    }

    @Override
    public synchronized void setCooldown(ServerPlayer player, int cooldownTicks) {
        cooldown.put(player.getUUID(), cooldownTicks);
    }

    @Override
    public synchronized boolean isOnCooldown(ServerPlayer player) {
        return cooldown.containsKey(player.getUUID());
    }

    @Override
    public synchronized void resetCooldown(ServerPlayer player) {
        cooldown.removeInt(player.getUUID());
    }

    @Override
    public synchronized void resetAll() {
        cooldown.clear();
    }

    @Override
    public synchronized void setOnCooldownOver(@Nullable Consumer<ServerPlayer> onCooldownOver) {
        this.onCooldownOver = onCooldownOver;
    }
}
