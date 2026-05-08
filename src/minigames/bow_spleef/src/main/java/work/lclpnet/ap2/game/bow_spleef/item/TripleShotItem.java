package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import work.lclpnet.ap2.core.hook.RangedWeaponUsedCallback;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;

public class TripleShotItem implements SpecialItem {

    @Override
    public String id() {
        return "triple_shot";
    }

    @Override
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.ARROW, 3);
    }

    @Override
    public void onPickedUp(ServerPlayer player, ItemStack stack, SpecialItemContext ctx) {
        ItemStack bow = player.getInventory().getItem(4);
        addEnchant(bow, player.level().registryAccess());

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.4f, 1.35f);
    }

    @Override
    public void onDropped(ServerPlayer player) {
        removeEnchant(player.getInventory().getItem(4));
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        RangedWeaponUsedCallback.HOOK.registerWith(hooks, (entity, stack, _) -> {
            if (!(entity instanceof ServerPlayer player)
                    || stack != player.getInventory().getItem(4)
                    || !ctx.hasSpecialItem(player, this)) return;

            removeEnchant(stack);

            ctx.removeSpecialItem(player, TripleShotItem.this);
        });
    }

    private void addEnchant(ItemStack bow, RegistryAccess registryManager) {
        var multiShot = ItemHelper.getEnchantment(Enchantments.MULTISHOT, registryManager);
        bow.enchant(multiShot, 1);

        bow.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    private void removeEnchant(ItemStack stack) {
        EnchantmentHelper.updateEnchantments(stack, builder -> builder.removeIf(enchant ->
                enchant.unwrapKey().orElse(null) == Enchantments.MULTISHOT));

        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
    }
}
