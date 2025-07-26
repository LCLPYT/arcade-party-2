package work.lclpnet.ap2.game.paintball.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.Potions;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.ParticleHelper;
import work.lclpnet.ap2.impl.util.SoundHelper;

public class MedKitItem implements SpecialItem {

    private static final float HEAL_PERCENT = 0.75f;

    @Override
    public String id() {
        return "med_kit";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        ItemStack stack = new ItemStack(Items.POTION);

        stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.STRONG_HEALING));

        return stack;
    }

    @Override
    public boolean canBePickedUp(ServerPlayerEntity player) {
        return player.getHealth() < player.getMaxHealth();
    }

    @Override
    public boolean shouldTransferToInventory(ServerPlayerEntity player) {
        return false;
    }

    @Override
    public void onPickedUp(ServerPlayerEntity player, ItemStack stack, SpecialItemContext ctx) {
        player.heal(player.getMaxHealth() * HEAL_PERCENT);
        SoundHelper.playSoundAt(player, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.5f, 1f);
        ParticleHelper.spawnParticleAt(player, ParticleTypes.HEART, 50, 1, 1, 1, 0);
    }
}
