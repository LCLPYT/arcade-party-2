package work.lclpnet.ap2.impl.util.world;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.gaco.collisions.util.PlayerAction;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.UUID;

public class CombatIdleManager {

    private final Participants participants;
    private final int triggerTicks;
    private final Object2IntMap<UUID> outOfCombat = new Object2IntOpenHashMap<>();
    private final Hook<PlayerAction> onEnterIdle = HookFactory.createArrayBacked(PlayerAction.class, hooks -> (player) -> {
        for (var hook : hooks) {
            hook.act(player);
        }
    });
    private final Hook<PlayerAction> onLeaveIdle = HookFactory.createArrayBacked(PlayerAction.class, hooks -> (player) -> {
        for (var hook : hooks) {
            hook.act(player);
        }
    });

    public CombatIdleManager(Participants participants, int triggerTicks) {
        this.participants = participants;
        this.triggerTicks = triggerTicks;

    }

    public Hook<PlayerAction> onEnterIdle() {
        return onEnterIdle;
    }

    public Hook<PlayerAction> onLeaveIdle() {
        return onLeaveIdle;
    }

    public void enable(TaskScheduler scheduler, HookRegistrar hooks) {
        scheduler.interval(this::tick, 1);

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity player) {
                onAttack(player);
            }

            return true;
        });
    }

    public void onAttack(ServerPlayerEntity player) {
        if (!participants.isParticipating(player)) return;

        int before = outOfCombat.put(player.getUuid(), 0);

        if (before >= triggerTicks) {
            onLeaveIdle.invoker().act(player);
        }
    }

    private void tick() {
        outOfCombat.keySet().removeIf(uuid -> !participants.isParticipating(uuid));

        for (ServerPlayerEntity player : participants) {
            int ticks = outOfCombat.computeInt(player.getUuid(), (uuid, t) -> t == null ? 1 : t + 1);

            if (ticks == triggerTicks) {
                onEnterIdle.invoker().act(player);
            }
        }
    }
}
