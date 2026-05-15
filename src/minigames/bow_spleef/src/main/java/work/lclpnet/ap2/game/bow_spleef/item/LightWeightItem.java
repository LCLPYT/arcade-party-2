package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.scheduler.Ticks;

import static net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY;
import static work.lclpnet.ap2.impl.util.EntityUtil.resetAttribute;
import static work.lclpnet.ap2.impl.util.EntityUtil.setAttribute;

public class LightWeightItem implements SpecialItem {

    private static final int DURATION = Ticks.seconds(5);

    @Override
    public String id() {
        return "light_weight";
    }

    @Override
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.FEATHER);
    }

    @Override
    public boolean canBeDropped(ServerPlayer player, ItemStack stack) {
        return false;
    }

    @Override
    public void onPickedUp(ServerPlayer player, ItemStack stack, SpecialItemContext ctx) {
        player.getCooldowns().addCooldown(stack, DURATION);
        setAttribute(player, GRAVITY, 0.035);

        ctx.scheduler().timeout(() -> {
            resetAttribute(player, GRAVITY);
            ctx.removeSpecialItem(player, this);
        }, DURATION);

        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.65f, 1.5f);
    }
}
