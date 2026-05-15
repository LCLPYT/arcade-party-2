package work.lclpnet.ap2.game.knockout;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import work.lclpnet.ap2.api.actor.ActorSpawnedCallback;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.knockout.util.ImpactDetector;
import work.lclpnet.ap2.impl.actor.GravityFieldActor;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.world.CombatIdleManager;
import work.lclpnet.ap2.impl.util.world.DestroyStageManager;
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector;
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver;
import work.lclpnet.game.impl.prot.ProtectionTypes;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityDamageCallback;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.text.LocalizedFormat;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.UUID;

import static java.lang.Math.round;
import static java.lang.Math.sqrt;
import static net.minecraft.ChatFormatting.*;

public class KnockoutInstance extends EliminationGameInstance {

    private static final double
            CHARGE_INCREMENT = 0.075,
            CHARGE_CRITICAL_INCREMENT = 0.08,
            CRITICAL_THRESHOLD = 2.5,
            IMPACT_STRENGTH_THRESHOLD = 0.6,
            MIN_IMPACT_CHARGE = 1.6,
            IMPACT_DESTRUCTION_MULTIPLIER = 0.35,
            IDLE_DAMAGE_MULTIPLIER = 3.0;

    private static final int IDLE_GLOW_TICKS = Ticks.seconds(15);

    private final Object2DoubleMap<UUID> charge = new Object2DoubleOpenHashMap<>();
    private final Object2BooleanMap<UUID> hit = new Object2BooleanOpenHashMap<>();
    private final Object2DoubleMap<BlockPos> blockDestruction = new Object2DoubleOpenHashMap<>();
    private final CombatIdleManager idleManager;

    private KnockoutWorldCrumble crumble = null;
    private ImpactDetector impactDetector;
    private DestroyStageManager destroyStageManager;

    public KnockoutInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useOldCombat();

        idleManager = new CombatIdleManager(gameHandle.getParticipants(), IDLE_GLOW_TICKS);
    }

    @Override
    public void start() {
        var movementObserver = new PlayerMovementObserver(new ChunkedCollisionDetector(), gameHandle.getParticipants()::isParticipating);
        var gravityManipulator = new GravityFieldActor.Manipulator();
        HookRegistrar hooks = gameHandle.getHooks();

        movementObserver.init(hooks, gameHandle.getServer());

        ActorSpawnedCallback.HOOK.registerWith(hooks, actor -> {
            if (actor instanceof GravityFieldActor gravityField) {
                gravityField.enable(movementObserver, gravityManipulator, gameHandle.getHooks());
            }
        });

        super.start();
    }

    @Override
    protected void prepare() {
        useRemainingPlayersDisplay();

        commons().whenBelowCriticalHeight().then(this::eliminate);

        crumble = new KnockoutWorldCrumble(getWorld(), getMap());
        crumble.init();
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> ProtectionTypes.ALLOW_DAMAGE.allow(config, this::canDamage));

        HookRegistrar hooks = gameHandle.getHooks();
        TaskScheduler scheduler = gameHandle.getScheduler();
        Participants participants = gameHandle.getParticipants();

        EntityDamageCallback.HOOK.registerWith(hooks, (entity, source, damage) -> {
            if (entity instanceof ServerPlayer player
                    && source.getEntity() instanceof ServerPlayer attacker
                    && player.hurtTime <= 0) {  // prevent duplicate damage during grace period
                this.onDamage(player, attacker, damage);
                return true;
            }

            return false;
        });

        int delaySeconds = crumble.getDelaySeconds();
        scheduler.timeout(this::beginCrumble, Ticks.seconds(delaySeconds));

        var debugController = commons().debugController();
        impactDetector = new ImpactDetector(participants, debugController, 0.1);
        destroyStageManager = new DestroyStageManager(getWorld());

        impactDetector.enable(scheduler);
        impactDetector.onImpact().register(this::onImpact);
        impactDetector.onMiss().register(this::onMiss);

        scheduler.interval(this::sendChargeDisplay, Ticks.seconds(2));

        idleManager.onEnterIdle().register(player -> {
            gameHandle.getTranslations()
                    .translateText("game.ap2.knockout.idle")
                    .formatted(YELLOW)
                    .sendTo(player);

            player.level().sendParticles(ParticleTypes.WITCH, player.getX(), player.getY(), player.getZ(), 50, 0.5, 1.0, 0.5, 0.1);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.8f, 1f);
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 1, false, false, true));
        });

        idleManager.onLeaveIdle().register(player -> player.removeEffect(MobEffects.GLOWING));

        idleManager.enable(scheduler, hooks);
    }

    @Override
    public void participantRemoved(@NonNull ServerPlayer player) {
        getData().add(player, chargeDetail(player));

        if (gameHandle.getParticipants().count() == 1) {
            ServerPlayer winner = gameHandle.getParticipants().iterator().next();
            getData().add(winner, chargeDetail(winner));
        }

        super.participantRemoved(player);
    }

    private @NotNull TranslatedText chargeDetail(ServerPlayer player) {
        return TranslatedText.create(
                lang -> RootText.create().append(formattedCharge(chargeOf(player)).translateTo(lang)),
                gameHandle.getTranslations()::getLanguage
        );
    }

    private LocalizedFormat formattedCharge(double charge) {
        return LocalizedFormat.format("%d%%", (int) round(charge * 100));
    }

    private void sendChargeDisplay() {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            sendCharge(player);
        }
    }

    private void beginCrumble() {
        crumble.start(gameHandle.getScheduler());
    }

    private boolean canDamage(Entity entity, DamageSource source) {
        Participants participants = gameHandle.getParticipants();

        return source.is(DamageTypes.PLAYER_ATTACK) && entity instanceof ServerPlayer player
                && !winManager.isGameOver()
                && source.getEntity() instanceof ServerPlayer attacker
                && participants.isParticipating(player) && participants.isParticipating(attacker);
    }

    private void onDamage(ServerPlayer player, ServerPlayer attacker, float damage) {
        double increment = damage > 2.0 ? CHARGE_CRITICAL_INCREMENT : CHARGE_INCREMENT;

        if (idleManager.isOutOfCombat(player)) {
            increment *= IDLE_DAMAGE_MULTIPLIER;
            
            idleManager.resetCombat(player);
        }

        double finalIncrement = increment;
        double power = charge.computeDouble(player.getUUID(), (_, old)
                -> (old == null ? 0 : old) + finalIncrement);

        synchronized (this) {
            hit.put(player.getUUID(), true);
        }

        Vec3 vec = player.position().subtract(attacker.position()).normalize();
        vec = new Vec3(vec.x(), 0.1, vec.z());
        vec = vec.scale(power);

        VelocityModifier.setVelocity(player, vec);

        ServerLevel world = player.level();

        double x = player.getX(), y = player.getY(), z = player.getZ();
        world.sendParticles(ParticleTypes.CLOUD, x, y, z, 25, 0.25, 0.25, 0.25, 0.1);

        world.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5f, 1.2f);
        world.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.5f, 1.25f);

        if (power > CRITICAL_THRESHOLD) {
            world.playSound(null, x, y, z, SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 0.25f, 1.25f);
            world.sendParticles(ParticleTypes.RAID_OMEN, x, y + 1, z, 10, 0.5, 0.5, 0.5, 0.1);
        }

        sendCharge(player);

        impactDetector.checkImpact(player, vec);
    }

    private void sendCharge(ServerPlayer player) {
        double charge = chargeOf(player);

        player.sendOverlayMessage(formattedCharge(charge)
                .translateTo(gameHandle.getTranslations().getLanguage(player))
                .copy().withStyle( charge > CRITICAL_THRESHOLD ? DARK_RED : WHITE));
    }

    private void onImpact(ServerPlayer player, Iterable<BlockPos> collisions) {
        synchronized (this) {
            if (!hit.put(player.getUUID(), false)) return;
        }

        Vec3 velocity = impactDetector.getVelocity(player);

        if (velocity == null) return;

        double charge = chargeOf(player);

        if (charge < MIN_IMPACT_CHARGE) return;

        double strength = velocity.length();

        if (strength < IMPACT_STRENGTH_THRESHOLD) return;

        double damage = sqrt(strength - IMPACT_STRENGTH_THRESHOLD) * IMPACT_DESTRUCTION_MULTIPLIER;
        ServerLevel world = getWorld();

        boolean anyBroke = false;

        for (BlockPos mutable : collisions) {
            BlockPos pos = mutable.immutable();
            double destruction = blockDestruction.compute(pos, (_, prev) -> prev == null ? damage : prev + damage);

            if (destruction < 1.d) {
                destroyStageManager.setDestroyStage(pos, (int) (destruction * 10));
                continue;
            }

            destroyStageManager.removeDestroyStage(pos);

            world.destroyBlock(pos, false);
            anyBroke = true;
        }

        if (anyBroke) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 0.2f, 1.15f);
        } else {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 0.18f, 0.85f);
        }
    }

    private double chargeOf(ServerPlayer player) {
        return charge.getOrDefault(player.getUUID(), 0.0);
    }

    private synchronized void onMiss(ServerPlayer player) {
        hit.put(player.getUUID(), false);
    }
}
