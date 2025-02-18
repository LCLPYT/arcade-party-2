package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.scheduler.Ticks;

import static net.minecraft.entity.attribute.EntityAttributes.GRAVITY;
import static work.lclpnet.lobby.util.PlayerReset.resetAttribute;
import static work.lclpnet.lobby.util.PlayerReset.setAttribute;

public class LightWeightItem implements SpecialItem {

    private static final int DURATION = Ticks.seconds(5);

    @Override
    public String id() {
        return "light_weight";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.FEATHER);
    }

    @Override
    public boolean canBeDropped(ServerPlayerEntity player, ItemStack stack) {
        return false;
    }

    @Override
    public void onPickedUp(ServerPlayerEntity player, ItemStack stack, SpecialItemContext ctx) {
        player.getItemCooldownManager().set(stack, DURATION);
        setAttribute(player, GRAVITY, 0.035);

        ctx.scheduler().timeout(() -> {
            resetAttribute(player, GRAVITY);
            ctx.removeSpecialItem(player, this);
        }, DURATION);

        player.getServerWorld().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ENTITY_BREEZE_IDLE_GROUND, SoundCategory.PLAYERS, 0.65f, 1.5f);
    }
}
