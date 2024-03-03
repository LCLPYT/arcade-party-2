package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.border.WorldBorder;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.api.util.action.PlayerAction;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.math.Vec2i;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Objects;

public class GameCommons {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;

    public GameCommons(MiniGameHandle gameHandle, GameMap map, ServerWorld world) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
    }

    public Action<PlayerAction> whenBelowCriticalHeight() {
        Objects.requireNonNull(map);

        Number minY = map.getProperty("critical-height");

        if (minY == null) return Action.noop();

        return whenBelowY(minY.doubleValue());
    }

    public Action<PlayerAction> whenBelowY(double minY) {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();

        var hook = HookFactory.createArrayBacked(PlayerAction.class, callbacks -> player -> {
            for (PlayerAction callback : callbacks) {
                callback.onAction(player);
            }
        });

        hooks.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            if (!participants.isParticipating(player)) return false;

            if (!(to.getY() >= minY)) {
                hook.invoker().onAction(player);
            }

            return false;
        });

        return Action.create(hook);
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks) {
        return scheduleWorldBorderShrink(delayTicks, durationTicks, 0);
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks, long finalDelayTicks) {
        TaskScheduler scheduler = gameHandle.getGameScheduler();

        var hook = HookFactory.createArrayBacked(Runnable.class, callbacks -> () -> {
            for (Runnable callback : callbacks) {
                callback.run();
            }
        });

        scheduler.timeout(() -> {
            WorldBorder worldBorder = shrinkWorldBorder();
            worldBorder.interpolateSize(worldBorder.getSize(), 5, durationTicks * 50L);

            for (ServerPlayerEntity player : PlayerLookup.world(world)) {
                player.playSound(SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 1, 0);
            }
        }, delayTicks);

        scheduler.timeout(hook.invoker(), delayTicks + durationTicks + finalDelayTicks);

        return Action.create(hook);
    }

    public WorldBorder shrinkWorldBorder() {
        if (!(map.getProperty("world-border") instanceof JSONObject wbConfig)) {
            throw new IllegalStateException("Object property \"world-border\" not set in map properties");
        }

        int centerX = 0, centerZ = 0;

        if (wbConfig.has("center")) {
            Vec2i center = MapUtil.readVec2i(wbConfig.getJSONArray("center"));

            centerX = center.x();
            centerZ = center.z();
        }

        int radius = wbConfig.getInt("size");
        if (radius % 2 == 0) radius += 1;

        WorldBorderManager manager = gameHandle.getWorldBorderManager();
        manager.setupWorldBorder(world);

        WorldBorder worldBorder = gameHandle.getWorldBorderManager().getWorldBorder();
        worldBorder.setCenter(centerX + 0.5, centerZ + 0.5);
        worldBorder.setSize(radius);
        worldBorder.setSafeZone(0);
        worldBorder.setDamagePerBlock(0.8);

        return worldBorder;
    }
}
