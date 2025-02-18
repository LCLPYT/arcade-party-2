package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import work.lclpnet.ap2.core.hook.RangedWeaponUsedCallback;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.kibu.hook.HookRegistrar;

public class TripleShotPowerup implements SpecialItem {

    public static final String ID = "triple_shot";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.ARROW, 3);
    }

    @Override
    public void onPickedUp(ServerPlayerEntity player) {
        ItemStack bow = player.getInventory().getStack(4);
        var multiShot = ItemHelper.getEnchantment(Enchantments.MULTISHOT, player.getWorld().getRegistryManager());
        bow.addEnchantment(multiShot, 1);

        bow.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

        player.playSoundToPlayer(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.4f, 1.35f);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(RangedWeaponUsedCallback.HOOK, (entity, stack, remainingUseTicks) -> {
            if (!(entity instanceof ServerPlayerEntity player)
                    || stack != player.getInventory().getStack(4)
                    || !ctx.hasSpecialItem(player, this)) return;

            EnchantmentHelper.apply(stack, builder -> builder.remove(enchant ->
                    enchant.getKey().orElse(null) == Enchantments.MULTISHOT));

            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);

            ctx.removeSpecialItem(player, TripleShotPowerup.this);
        });
    }
}
