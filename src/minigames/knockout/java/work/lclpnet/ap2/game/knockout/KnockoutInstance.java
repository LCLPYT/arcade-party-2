package work.lclpnet.ap2.game.knockout;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
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
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityDamageCallback;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.text.LocalizedFormat;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.UUID;

import static java.lang.Math.round;
import static java.lang.Math.sqrt;
import static net.minecraft.util.Formatting.*;

public class KnockoutInstance extends EliminationGameInstance {

    private static final double
            CHARGE_INCREMENT = 0.075,
            CHARGE_CRITICAL_INCREMENT = 0.08,
            CRITICAL_THRESHOLD = 2.5,
            IMPACT_STRENGTH_THRESHOLD = 0.6,
            MIN_IMPACT_CHARGE = 1.6,
            IMPACT_DESTRUCTION_MULTIPLIER = 0.35;

    private static final int IDLE_GLOW_TICKS = Ticks.seconds(15);

    private final Object2DoubleMap<UUID> charge = new Object2DoubleOpenHashMap<>();
    private final Object2BooleanMap<UUID> hit = new Object2BooleanOpenHashMap<>();
    private final Object2DoubleMap<BlockPos> blockDestruction = new Object2DoubleOpenHashMap<>();
    private KnockoutWorldCrumble crumble = null;
    private ImpactDetector impactDetector;
    private DestroyStageManager destroyStageManager;

    public KnockoutInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useOldCombat();
    }

    @Override
    public void start() {
        var movementObserver = new PlayerMovementObserver(new ChunkedCollisionDetector(), gameHandle.getParticipants()::isParticipating);
        var gravityManipulator = new GravityFieldActor.Manipulator();
        HookRegistrar hooks = gameHandle.getHooks();

        movementObserver.init(hooks, gameHandle.getServer());

        hooks.registerHook(ActorSpawnedCallback.HOOK, actor -> {
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
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, this::canDamage));

        HookRegistrar hooks = gameHandle.getHooks();
        TaskScheduler scheduler = gameHandle.getScheduler();
        Participants participants = gameHandle.getParticipants();

        hooks.registerHook(EntityDamageCallback.HOOK, (entity, source, damage) -> {
            if (entity instanceof ServerPlayerEntity player
                    && source.getAttacker() instanceof ServerPlayerEntity attacker
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

        var idleManager = new CombatIdleManager(participants, IDLE_GLOW_TICKS);

        idleManager.onEnterIdle().register(player -> {
            gameHandle.getTranslations()
                    .translateText("game.ap2.knockout.idle")
                    .formatted(YELLOW)
                    .sendTo(player);

            player.getEntityWorld().spawnParticles(ParticleTypes.WITCH, player.getX(), player.getY(), player.getZ(), 50, 0.5, 1.0, 0.5, 0.1);
            player.playSoundToPlayer(SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.PLAYERS, 0.8f, 1f);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, Integer.MAX_VALUE, 1, false, false, true));
        });

        idleManager.onLeaveIdle().register(player -> player.removeStatusEffect(StatusEffects.GLOWING));

        idleManager.enable(scheduler, hooks);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        getData().add(player, chargeDetail(player));

        if (gameHandle.getParticipants().count() == 1) {
            ServerPlayerEntity winner = gameHandle.getParticipants().iterator().next();
            getData().add(winner, chargeDetail(winner));
        }

        super.participantRemoved(player);
    }

    private @NotNull TranslatedText chargeDetail(ServerPlayerEntity player) {
        return TranslatedText.create(
                lang -> RootText.create().append(formattedCharge(chargeOf(player)).translateTo(lang)),
                gameHandle.getTranslations()::getLanguage
        );
    }

    private LocalizedFormat formattedCharge(double charge) {
        return LocalizedFormat.format("%d%%", (int) round(charge * 100));
    }

    private void sendChargeDisplay() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            sendCharge(player);
        }
    }

    private void beginCrumble() {
        crumble.start(gameHandle.getScheduler());
    }

    private boolean canDamage(Entity entity, DamageSource source) {
        Participants participants = gameHandle.getParticipants();

        return source.isOf(DamageTypes.PLAYER_ATTACK) && entity instanceof ServerPlayerEntity player
                && !winManager.isGameOver()
                && source.getAttacker() instanceof ServerPlayerEntity attacker
                && participants.isParticipating(player) && participants.isParticipating(attacker);
    }

    private void onDamage(ServerPlayerEntity player, ServerPlayerEntity attacker, float damage) {
        double increment = damage > 2.0 ? CHARGE_CRITICAL_INCREMENT : CHARGE_INCREMENT;
        double power = charge.computeDouble(player.getUuid(), (uuid, old) -> (old == null ? 0 : old) + increment);

        synchronized (this) {
            hit.put(player.getUuid(), true);
        }

        Vec3d vec = player.getEntityPos().subtract(attacker.getEntityPos()).normalize();
        vec = new Vec3d(vec.getX(), 0.1, vec.getZ());
        vec = vec.multiply(power);

        VelocityModifier.setVelocity(player, vec);

        ServerWorld world = player.getEntityWorld();

        double x = player.getX(), y = player.getY(), z = player.getZ();
        world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 25, 0.25, 0.25, 0.25, 0.1);

        world.playSound(null, x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 0.5f, 1.2f);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.5f, 1.25f);

        if (power > CRITICAL_THRESHOLD) {
            world.playSound(null, x, y, z, SoundEvents.ENTITY_ALLAY_HURT, SoundCategory.PLAYERS, 0.25f, 1.25f);
            world.spawnParticles(ParticleTypes.RAID_OMEN, x, y + 1, z, 10, 0.5, 0.5, 0.5, 0.1);
        }

        sendCharge(player);

        impactDetector.checkImpact(player, vec);
    }

    private void sendCharge(ServerPlayerEntity player) {
        double charge = chargeOf(player);

        player.sendMessage(formattedCharge(charge)
                .translateTo(gameHandle.getTranslations().getLanguage(player))
                .copy().formatted( charge > CRITICAL_THRESHOLD ? DARK_RED : WHITE), true);
    }

    private void onImpact(ServerPlayerEntity player, Iterable<BlockPos> collisions) {
        synchronized (this) {
            if (!hit.put(player.getUuid(), false)) return;
        }

        Vec3d velocity = impactDetector.getVelocity(player);

        if (velocity == null) return;

        double charge = chargeOf(player);

        if (charge < MIN_IMPACT_CHARGE) return;

        double strength = velocity.length();

        if (strength < IMPACT_STRENGTH_THRESHOLD) return;

        double damage = sqrt(strength - IMPACT_STRENGTH_THRESHOLD) * IMPACT_DESTRUCTION_MULTIPLIER;
        ServerWorld world = getWorld();

        boolean anyBroke = false;

        for (BlockPos mutable : collisions) {
            BlockPos pos = mutable.toImmutable();
            double destruction = blockDestruction.compute(pos, (p, prev) -> prev == null ? damage : prev + damage);

            if (destruction < 1.d) {
                destroyStageManager.setDestroyStage(pos, (int) (destruction * 10));
                continue;
            }

            destroyStageManager.removeDestroyStage(pos);

            world.breakBlock(pos, false);
            anyBroke = true;
        }

        if (anyBroke) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.BLOCKS, 0.2f, 1.15f);
        } else {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.BLOCKS, 0.18f, 0.85f);
        }
    }

    private double chargeOf(ServerPlayerEntity player) {
        return charge.getOrDefault(player.getUuid(), 0.0);
    }

    private synchronized void onMiss(ServerPlayerEntity player) {
        hit.put(player.getUuid(), false);
    }
}
