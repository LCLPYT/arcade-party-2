package work.lclpnet.ap2.game.dragon_escape.kit;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.function.Predicate;

public class LeapKit extends BaseKit {

    public static final String ID = "leap";

    private static final Item ITEM = Items.IRON_AXE;
    private static final int
            USES = 3,
            COOLDOWN_TICKS = Ticks.seconds(3);
    private static final double LEAP_STRENGTH = 1.8;

    private final Predicate<ServerPlayerEntity> canUse;

    public LeapKit(KitHandle handle, Predicate<ServerPlayerEntity> canUse) {
        super(handle, ID);
        this.canUse = canUse;
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager manager) {
        return new ItemStack(Items.IRON_AXE);
    }

    @Override
    public void init() {
        handle.hooks().registerHook(PlayerInteractionHooks.USE_ITEM, (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayerEntity player)) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);

            if (stack.isOf(ITEM) && !player.getItemCooldownManager().isCoolingDown(stack) && canUse.test(player)) {
                useItem(player, stack);
            }

            return ActionResult.PASS;
        });
    }

    @Override
    public void equip(ServerPlayerEntity player) {
        ItemStack stack = handle.createItemStack(this, player);
        stack.setCount(USES);

        player.getInventory().setStack(0, stack);
    }

    @Override
    public void unequip(ServerPlayerEntity player) {
        player.getInventory().removeStack(0);
    }

    private void useItem(ServerPlayerEntity player, ItemStack stack) {
        stack.decrementUnlessCreative(1, player);

        player.getItemCooldownManager().set(stack, COOLDOWN_TICKS);

        VelocityModifier.setVelocity(player, player.getRotationVector().multiply(LEAP_STRENGTH));

        ServerWorld world = player.getServerWorld();

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.5f, 1.8f);

        world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 50,
                0.2, 0.2, 0.2, 0.8);
    }
}
