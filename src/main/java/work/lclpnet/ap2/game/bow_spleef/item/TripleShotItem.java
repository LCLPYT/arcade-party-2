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

public class TripleShotItem implements SpecialItem {

    @Override
    public String id() {
        return "triple_shot";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.ARROW, 3);
    }

    @Override
    public void onPickedUp(ServerPlayerEntity player, ItemStack stack, SpecialItemContext ctx) {
        ItemStack bow = player.getInventory().getStack(4);
        addEnchant(bow, player.getWorld().getRegistryManager());

        player.playSoundToPlayer(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.4f, 1.35f);
    }

    @Override
    public void onDropped(ServerPlayerEntity player) {
        removeEnchant(player.getInventory().getStack(4));
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(RangedWeaponUsedCallback.HOOK, (entity, stack, remainingUseTicks) -> {
            if (!(entity instanceof ServerPlayerEntity player)
                    || stack != player.getInventory().getStack(4)
                    || !ctx.hasSpecialItem(player, this)) return;

            removeEnchant(stack);

            ctx.removeSpecialItem(player, TripleShotItem.this);
        });
    }

    private void addEnchant(ItemStack bow, DynamicRegistryManager registryManager) {
        var multiShot = ItemHelper.getEnchantment(Enchantments.MULTISHOT, registryManager);
        bow.addEnchantment(multiShot, 1);

        bow.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    private void removeEnchant(ItemStack stack) {
        EnchantmentHelper.apply(stack, builder -> builder.remove(enchant ->
                enchant.getKey().orElse(null) == Enchantments.MULTISHOT));

        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
    }
}
