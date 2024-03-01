package work.lclpnet.ap2.game.knockout;

import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.entity.EntityDamageCallback;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.UUID;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class KnockoutInstance extends EliminationGameInstance {

    private static final double CHARGE_INCREMENT = 0.075, CRITICAL_THRESHOLD = 2.5;
    private final Object2DoubleMap<UUID> charge = new Object2DoubleArrayMap<>();

    public KnockoutInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useOldCombat();
    }

    @Override
    protected void prepare() {
        commons().whenBelowCriticalHeight().then(this::eliminate);
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, this::canDamage));

        gameHandle.getHookRegistrar().registerHook(EntityDamageCallback.HOOK, (entity, source, health) -> {
            if (entity instanceof ServerPlayerEntity player
                && source.getAttacker() instanceof ServerPlayerEntity attacker) {
                this.onDamage(player, attacker);
                return true;
            }

            return false;
        });
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
                        styled(String.format("%.2f", power * 100), chargeColor))
                .formatted(Formatting.GOLD, Formatting.BOLD);

        player.sendMessage(msg, true);
    }
}
