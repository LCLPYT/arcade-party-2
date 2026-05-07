package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.world.ExplosionUtil;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.util.Mth.lerp;

public class CreeperExplosionItem implements SpecialItem {

    private static final int DURATION_TICKS = 25;
    private final Map<UUID, TaskHandle> tasks = new HashMap<>();

    @Override
    public String id() {
        return "creeper_explosion";
    }

    @Override
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.CREEPER_HEAD);
    }

    @Override
    public boolean canBeDropped(ServerPlayer player, ItemStack stack) {
        return !tasks.containsKey(player.getUUID());
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        if (tasks.containsKey(player.getUUID())) return InteractionResult.FAIL;

        tasks.put(player.getUUID(), ctx.scheduler().interval(new SchedulerAction() {
            int t = 0;

            @Override
            public void run(RunningTask task) {
                if (player.hasDisconnected()) {
                    task.cancel();
                    return;
                }

                float pitch = lerp((float) t / DURATION_TICKS, 0.85f, 1.45f);

                ServerLevel world = player.level();
                world.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CREEPER_HURT, SoundSource.HOSTILE, 0.2f, pitch);
                world.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 10, 0.1, 0.1, 0.1, 0.15);

                if (t++ < DURATION_TICKS) return;

                task.cancel();
                ctx.removeSpecialItem(player, CreeperExplosionItem.this);

                var behaviour = new ExplosionDamageCalculator() {

                    @Override
                    public float getKnockbackMultiplier(Entity entity) {
                        return 2.5f;
                    }
                };

                world.explode(player, null, behaviour, player.getX(), player.getY(), player.getZ(),
                        3.5f, false,
                        Level.ExplosionInteraction.BLOCK, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                        ExplosionUtil.EXPLOSION_BLOCK_PARTICLES,
                        SoundEvents.GENERIC_EXPLODE);

                world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 1, 0.1, 0.1, 0.1, 0.15);
            }
        }, 1).whenComplete(() -> tasks.remove(player.getUUID())));

        player.getCooldowns().addCooldown(stack, DURATION_TICKS);
        PlayerUtils.syncPlayerItems(player);

        return InteractionResult.SUCCESS_SERVER;
    }
}
