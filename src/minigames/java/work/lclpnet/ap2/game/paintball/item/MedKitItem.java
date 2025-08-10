package work.lclpnet.ap2.game.paintball.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.attribute.EntityAttributes;
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
import work.lclpnet.lobby.util.PlayerReset;

public class MedKitItem implements SpecialItem {

    private static final float HEAL_PERCENT = 0.75f, ABSORPTION_AMOUNT = 2f;

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
    public boolean shouldTransferToInventory(ServerPlayerEntity player) {
        return false;
    }

    @Override
    public void onPickedUp(ServerPlayerEntity player, ItemStack stack, SpecialItemContext ctx) {
        if (player.getHealth() >= player.getMaxHealth()) {
            float absorption = player.getAbsorptionAmount() + ABSORPTION_AMOUNT;
            PlayerReset.setAttribute(player, EntityAttributes.MAX_ABSORPTION, absorption);
            player.setAbsorptionAmount(absorption);
        } else {
            player.heal(player.getMaxHealth() * HEAL_PERCENT);
        }

        SoundHelper.playSoundAt(player, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.5f, 1f);
        ParticleHelper.spawnParticleAt(player, ParticleTypes.HEART, 50, 1, 1, 1, 0);
    }
}
