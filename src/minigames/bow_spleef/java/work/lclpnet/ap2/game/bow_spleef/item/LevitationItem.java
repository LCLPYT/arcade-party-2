package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.scheduler.Ticks;

public class LevitationItem implements SpecialItem {

    private static final int DURATION = Ticks.seconds(3);

    @Override
    public String id() {
        return "levitation";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.BREEZE_ROD);
    }

    @Override
    public boolean canBeDropped(ServerPlayerEntity player, ItemStack stack) {
        return !player.getItemCooldownManager().isCoolingDown(stack);
    }

    @Override
    public ActionResult onUse(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        player.getItemCooldownManager().set(stack, DURATION);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, DURATION, 4));
        ctx.scheduler().timeout(() -> ctx.removeSpecialItem(player, this), DURATION);

        player.getEntityWorld().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, SoundCategory.PLAYERS, 0.5f, 2f);

        return ActionResult.SUCCESS_SERVER;
    }
}
