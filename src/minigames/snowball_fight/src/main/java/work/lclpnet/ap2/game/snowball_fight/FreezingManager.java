package work.lclpnet.ap2.game.snowball_fight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.core.hook.FrozenTickChangeCallback;
import work.lclpnet.ap2.core.hook.PowderedSnowSlowCallback;
import work.lclpnet.ap2.impl.util.world.CombatIdleManager;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.util.PlayerReset;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.lang.Math.*;
import static net.minecraft.ChatFormatting.YELLOW;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
import static net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH;
import static net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;

public class FreezingManager {

    private static final Identifier POWDER_SNOW_CANCEL_MODIFIER_ID = ApConstants.identifier("powder_snow_cancel");

    private final TaskScheduler scheduler;
    private final Translations translations;
    private final Participants participants;
    private final int freezingStartTicks;
    private final int freezingTicks;
    private final Map<UUID, TaskHandle> tasks = new HashMap<>();

    public FreezingManager(TaskScheduler scheduler, Translations translations, Participants participants, int freezingStartTicks, int freezingTicks) {
        this.scheduler = scheduler;
        this.translations = translations;
        this.participants = participants;
        this.freezingStartTicks = freezingStartTicks;
        this.freezingTicks = max(1, freezingTicks);
    }

    public void enable(HookRegistrar hooks) {
        var idleManager = new CombatIdleManager(participants, freezingStartTicks);

        idleManager.onEnterIdle().register(player -> {
            translations.translateText("game.ap2.snowball_fight.idle").formatted(YELLOW).sendTo(player);

            player.level().sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 50, 0.5, 1.0, 0.5, 0.1);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.5f);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.25f, 0.8f);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.2f, 1.8f);

            startFreezing(player);
        });

        idleManager.onLeaveIdle().register(this::stopFreezing);

        idleManager.enable(scheduler, hooks);

        FrozenTickChangeCallback.HOOK.registerWith(hooks, (entity, ticks)
                -> entity instanceof ServerPlayer player
                && ticks <= player.getTicksFrozen()
                && participants.isParticipating(player)
                && tasks.containsKey(player.getUUID()));

        PowderedSnowSlowCallback.ADD.registerWith(hooks, entity -> {
            if (!(entity instanceof ServerPlayer player)
                    || !participants.isParticipating(player)
                    || !tasks.containsKey(player.getUUID())) return false;

            // remove the airborne slow modifier when the real modifier gets active, more info below
            AttributeInstance instance = entity.getAttribute(MOVEMENT_SPEED);

            if (instance == null || !instance.hasModifier(POWDER_SNOW_CANCEL_MODIFIER_ID)) return false;

            instance.removeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID);

            return false;
        });

        PowderedSnowSlowCallback.REMOVE.registerWith(hooks, entity -> {
            if (!(entity instanceof ServerPlayer player)
                    || !participants.isParticipating(player)
                    || !tasks.containsKey(player.getUUID())
                    || player.getTicksFrozen() <= 0) return false;

            // frozen slow is also applied on the client side. When airborne, the slow effect will be removed, causing FOV flicker.
            // therefore add another temporary modifier for to slow in the air
            AttributeInstance instance = entity.getAttribute(MOVEMENT_SPEED);

            if (instance == null) return false;

            float cancellationFactor = -0.05F * player.getPercentFrozen();

            instance.addTransientModifier(new AttributeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID, cancellationFactor, ADD_VALUE));

            return false;
        });
    }

    public void startFreezing(ServerPlayer player) {
        // prevent jumping to prevent fov flicker
        PlayerReset.setAttribute(player, JUMP_STRENGTH, 0);

        var prevTask = tasks.put(player.getUUID(), scheduler.interval(new SchedulerAction() {
            int time = 0;

            @Override
            public void run(RunningTask task) {
                if (player.hasDisconnected() || !player.isAlive()) {
                    task.cancel();
                    return;
                }

                int t = time++;
                double progress = clamp(t / (double) freezingTicks, 0.0, 1.0);
                int frozenTicks = (int) round(player.getTicksRequiredToFreeze() * progress);

                player.setTicksFrozen(frozenTicks);

                if (t >= freezingTicks) {
                    task.cancel();
                }
            }
        }, 1));

        if (prevTask != null) {
            prevTask.cancel();
        }
    }

    public void stopFreezing(ServerPlayer player) {
        TaskHandle task = tasks.remove(player.getUUID());

        if (task == null) return;

        task.cancel();

        player.setTicksFrozen(0);
        PlayerReset.resetAttribute(player, JUMP_STRENGTH);

        AttributeInstance instance = player.getAttribute(MOVEMENT_SPEED);

        if (instance != null && instance.hasModifier(POWDER_SNOW_CANCEL_MODIFIER_ID)) {
            instance.removeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID);
        }
    }
}
