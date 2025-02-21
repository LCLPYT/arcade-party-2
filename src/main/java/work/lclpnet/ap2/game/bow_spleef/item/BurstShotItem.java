package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import work.lclpnet.ap2.core.hook.RangedWeaponUsedCallback;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.hook.HookRegistrar;

public class BurstShotItem implements SpecialItem {

    private static final int
            BURST_COUNT = 4,
            BURST_INTERVAL_TICKS = 2;

    @Override
    public String id() {
        return "burst_shot";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.BLAZE_POWDER);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(RangedWeaponUsedCallback.HOOK, (entity, stack, remainingUseTicks) -> {
            if (!(entity instanceof ServerPlayerEntity player)
                    || stack != player.getInventory().getStack(4)
                    || !(stack.getItem() instanceof BowItem bow)
                    || !ctx.hasSpecialItem(player, this)) return;

            ctx.removeSpecialItem(player, this);
            int useTicks = bow.getMaxUseTime(stack, player) - remainingUseTicks;

            for (int i = 1; i < BURST_COUNT; i++) {
                ctx.scheduler().timeout(() -> shoot(player, stack, useTicks), BURST_INTERVAL_TICKS * i);
            }
        });
    }

    private void shoot(ServerPlayerEntity player, ItemStack weaponStack, int useTicks) {
        ServerWorld world = player.getServerWorld();
        ItemStack projectileStack = new ItemStack(Items.ARROW);
        var arrow = ((ArrowItem) Items.ARROW).createArrow(world, projectileStack, player, weaponStack);
        float useProgress = BowItem.getPullProgress(useTicks);
        float speed = useProgress * 3.0F;

        ProjectileEntity.spawn(arrow, world, projectileStack, projectile ->
                projectile.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, speed, 1));

        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + useProgress * 0.5F);
    }
}
