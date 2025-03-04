package work.lclpnet.ap2.game.knockout;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.actor.ActorSpawnedCallback;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.game.knockout.util.ImpactDetector;
import work.lclpnet.ap2.impl.actor.GravityFieldActor;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.PlayerMovementObserver;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.DestroyStageManager;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityDamageCallback;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.UUID;

import static java.lang.Math.sqrt;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class KnockoutInstance extends EliminationGameInstance {

    private static final double
            CHARGE_INCREMENT = 0.075,
            CRITICAL_THRESHOLD = 2.5,
            IMPACT_STRENGTH_THRESHOLD = 0.6,
            MIN_IMPACT_CHARGE = 1.6;

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
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        movementObserver.init(hooks, gameHandle.getServer());

        hooks.registerHook(ActorSpawnedCallback.HOOK, actor -> {
            if (actor instanceof GravityFieldActor gravityField) {
                gravityField.enable(movementObserver, gravityManipulator);
            }
        });

        super.start();
    }

    @Override
    protected void prepare() {
        commons().whenBelowCriticalHeight().then(this::eliminate);

        crumble = new KnockoutWorldCrumble(getWorld(), getMap());
        crumble.init();
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, this::canDamage));

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(EntityDamageCallback.HOOK, (entity, source, health) -> {
            if (entity instanceof ServerPlayerEntity player
                    && source.getAttacker() instanceof ServerPlayerEntity attacker
                    && player.hurtTime <= 0) {  // prevent duplicate damage during grace period
                this.onDamage(player, attacker);
                return true;
            }

            return false;
        });

        int delaySeconds = crumble.getDelaySeconds();
        TaskScheduler scheduler = gameHandle.getGameScheduler();
        scheduler.timeout(this::beginCrumble, Ticks.seconds(delaySeconds));

        var debugController = new DebugController();
        impactDetector = new ImpactDetector(gameHandle.getParticipants(), debugController, 0.1);
        destroyStageManager = new DestroyStageManager(getWorld());

        if (ApConstants.DEBUG) {
            debugController.init(ApResources.getInstance(), getWorld());
        }

        impactDetector.enable(scheduler);
        impactDetector.onImpact().register(this::onImpact);
        impactDetector.onMiss().register(this::onMiss);
    }

    private void beginCrumble() {
        crumble.start(gameHandle.getGameScheduler());
    }

    private boolean canDamage(Entity entity, DamageSource source) {
        Participants participants = gameHandle.getParticipants();

        return source.isOf(DamageTypes.PLAYER_ATTACK) && entity instanceof ServerPlayerEntity player
               && !winManager.isGameOver()
               && source.getAttacker() instanceof ServerPlayerEntity attacker
               && participants.isParticipating(player) && participants.isParticipating(attacker);
    }

    private void onDamage(ServerPlayerEntity player, ServerPlayerEntity attacker) {
        double power = charge.computeDouble(player.getUuid(), (uuid, old) -> (old == null ? 0 : old) + CHARGE_INCREMENT);

        synchronized (this) {
            hit.put(player.getUuid(), true);
        }

        Vec3d vec = player.getPos().subtract(attacker.getPos()).normalize();
        vec = new Vec3d(vec.getX(), 0.1, vec.getZ());
        vec = vec.multiply(power);

        VelocityModifier.setVelocity(player, vec);

        ServerWorld world = player.getServerWorld();

        double x = player.getX(), y = player.getY(), z = player.getZ();
        world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 25, 0.25, 0.25, 0.25, 0.1);

        Formatting chargeColor;

        if (power > CRITICAL_THRESHOLD) {
            world.playSound(null, x, y, z, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.5f, 0.75f);
            chargeColor = Formatting.DARK_RED;
        } else {
            world.playSound(null, x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.5f, 1.25f);
            chargeColor = Formatting.WHITE;
        }

        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.knockout.charge",
                        styled("%.2f".formatted(power * 100), chargeColor))
                .formatted(Formatting.GOLD, Formatting.BOLD);

        player.sendMessage(msg, true);

        impactDetector.checkImpact(player, vec);
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

        double damage = sqrt(strength - IMPACT_STRENGTH_THRESHOLD) * 0.16;
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
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.BLOCKS, 0.25f, 1.15f);
        } else {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.BLOCKS, 0.25f, 0.85f);
        }
    }

    private double chargeOf(ServerPlayerEntity player) {
        return charge.getOrDefault(player.getUuid(), 0.0);
    }

    private synchronized void onMiss(ServerPlayerEntity player) {
        hit.put(player.getUuid(), false);
    }
}
