package work.lclpnet.ap2.game.snowball_fight;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.core.hook.FrozenTickChangeCallback;
import work.lclpnet.ap2.core.hook.PowderedSnowSlowCallback;
import work.lclpnet.ap2.impl.util.world.CombatIdleManager;
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
import static net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE;
import static net.minecraft.entity.attribute.EntityAttributes.JUMP_STRENGTH;
import static net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED;
import static net.minecraft.util.Formatting.YELLOW;

public class FreezingManager {

    private static final Identifier POWDER_SNOW_CANCEL_MODIFIER_ID = ArcadeParty.identifier("powder_snow_cancel");

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

            player.getServerWorld().spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 50, 0.5, 1.0, 0.5, 0.1);
            player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 0.5f);
            player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.25f, 0.8f);
            player.playSoundToPlayer(SoundEvents.ENTITY_BREEZE_IDLE_GROUND, SoundCategory.PLAYERS, 0.2f, 1.8f);

            startFreezing(player);
        });

        idleManager.onLeaveIdle().register(this::stopFreezing);

        idleManager.enable(scheduler, hooks);

        hooks.registerHook(FrozenTickChangeCallback.HOOK, (entity, ticks)
                -> entity instanceof ServerPlayerEntity player
                && ticks <= player.getFrozenTicks()
                && participants.isParticipating(player)
                && tasks.containsKey(player.getUuid()));

        hooks.registerHook(PowderedSnowSlowCallback.ADD, entity -> {
            if (!(entity instanceof ServerPlayerEntity player)
                    || !participants.isParticipating(player)
                    || !tasks.containsKey(player.getUuid())) return false;

            // remove the airborne slow modifier when the real modifier gets active, more info below
            EntityAttributeInstance instance = entity.getAttributeInstance(MOVEMENT_SPEED);

            if (instance == null || !instance.hasModifier(POWDER_SNOW_CANCEL_MODIFIER_ID)) return false;

            instance.removeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID);

            return false;
        });

        hooks.registerHook(PowderedSnowSlowCallback.REMOVE, entity -> {
            if (!(entity instanceof ServerPlayerEntity player)
                    || !participants.isParticipating(player)
                    || !tasks.containsKey(player.getUuid())
                    || player.getFrozenTicks() <= 0) return false;

            // frozen slow is also applied on the client side. When airborne, the slow effect will be removed, causing FOV flicker.
            // therefore add another temporary modifier for to slow in the air
            EntityAttributeInstance instance = entity.getAttributeInstance(MOVEMENT_SPEED);

            if (instance == null) return false;

            float cancellationFactor = -0.05F * player.getFreezingScale();

            instance.addTemporaryModifier(new EntityAttributeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID, cancellationFactor, ADD_VALUE));

            return false;
        });
    }

    public void startFreezing(ServerPlayerEntity player) {
        // prevent jumping to prevent fov flicker
        PlayerReset.setAttribute(player, JUMP_STRENGTH, 0);

        var prevTask = tasks.put(player.getUuid(), scheduler.interval(new SchedulerAction() {
            int time = 0;

            @Override
            public void run(RunningTask task) {
                if (player.isDisconnected() || !player.isAlive()) {
                    task.cancel();
                    return;
                }

                int t = time++;
                double progress = max(0.0, min(1.0, t / (double) freezingTicks));
                int frozenTicks = (int) round(player.getMinFreezeDamageTicks() * progress);

                player.setFrozenTicks(frozenTicks);

                if (t >= freezingTicks) {
                    task.cancel();
                }
            }
        }, 1));

        if (prevTask != null) {
            prevTask.cancel();
        }
    }

    public void stopFreezing(ServerPlayerEntity player) {
        TaskHandle task = tasks.remove(player.getUuid());

        if (task == null) return;

        task.cancel();

        player.setFrozenTicks(0);
        PlayerReset.resetAttribute(player, JUMP_STRENGTH);

        EntityAttributeInstance instance = player.getAttributeInstance(MOVEMENT_SPEED);

        if (instance != null && instance.hasModifier(POWDER_SNOW_CANCEL_MODIFIER_ID)) {
            instance.removeModifier(POWDER_SNOW_CANCEL_MODIFIER_ID);
        }
    }
}
