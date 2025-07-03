package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.util.math.MathHelper.lerp;

public class CreeperExplosionItem implements SpecialItem {

    private static final int DURATION_TICKS = 25;
    private final Map<UUID, TaskHandle> tasks = new HashMap<>();

    @Override
    public String id() {
        return "creeper_explosion";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.CREEPER_HEAD);
    }

    @Override
    public boolean canBeDropped(ServerPlayerEntity player, ItemStack stack) {
        return !tasks.containsKey(player.getUuid());
    }

    @Override
    public ActionResult onUse(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        if (tasks.containsKey(player.getUuid())) return ActionResult.FAIL;

        tasks.put(player.getUuid(), ctx.scheduler().interval(new SchedulerAction() {
            int t = 0;

            @Override
            public void run(RunningTask task) {
                if (player.isDisconnected()) {
                    task.cancel();
                    return;
                }

                float pitch = lerp((float) t / DURATION_TICKS, 0.85f, 1.45f);

                ServerWorld world = player.getWorld();
                world.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ENTITY_CREEPER_HURT, SoundCategory.HOSTILE, 0.2f, pitch);
                world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 10, 0.1, 0.1, 0.1, 0.15);

                if (t++ < DURATION_TICKS) return;

                task.cancel();
                ctx.removeSpecialItem(player, CreeperExplosionItem.this);

                var behaviour = new ExplosionBehavior() {

                    @Override
                    public float getKnockbackModifier(Entity entity) {
                        return 2.5f;
                    }
                };

                world.createExplosion(player, null, behaviour, player.getX(), player.getY(), player.getZ(),
                        3.5f, false,
                        World.ExplosionSourceType.BLOCK, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                        SoundEvents.ENTITY_GENERIC_EXPLODE);

                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 1, 0.1, 0.1, 0.1, 0.15);
            }
        }, 1).whenComplete(() -> tasks.remove(player.getUuid())));

        player.getItemCooldownManager().set(stack, DURATION_TICKS);
        PlayerUtils.syncPlayerItems(player);

        return ActionResult.SUCCESS_SERVER;
    }
}
