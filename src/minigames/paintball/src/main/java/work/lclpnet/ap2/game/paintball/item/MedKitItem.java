package work.lclpnet.ap2.game.paintball.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.ParticleHelper;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.game.util.PlayerReset;

public class MedKitItem implements SpecialItem {

    private static final float HEAL_PERCENT = 0.75f, ABSORPTION_AMOUNT = 2f;

    @Override
    public String id() {
        return "med_kit";
    }

    @Override
    public ItemStack createItemStack(RegistryAccess registryManager) {
        ItemStack stack = new ItemStack(Items.POTION);

        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.STRONG_HEALING));

        return stack;
    }

    @Override
    public boolean shouldTransferToInventory(ServerPlayer player) {
        return false;
    }

    @Override
    public void onPickedUp(ServerPlayer player, ItemStack stack, SpecialItemContext ctx) {
        if (player.getHealth() >= player.getMaxHealth()) {
            float absorption = player.getAbsorptionAmount() + ABSORPTION_AMOUNT;
            PlayerReset.setAttribute(player, Attributes.MAX_ABSORPTION, absorption);
            player.setAbsorptionAmount(absorption);
        } else {
            player.heal(player.getMaxHealth() * HEAL_PERCENT);
        }

        SoundHelper.playSoundAt(player, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.5f, 1f);
        ParticleHelper.spawnParticleAt(player, ParticleTypes.HEART, 50, 1, 1, 1, 0);
    }
}
