package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.BREEZE_ROD);
    }

    @Override
    public boolean canBeDropped(ServerPlayer player, ItemStack stack) {
        return !player.getCooldowns().isOnCooldown(stack);
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        player.getCooldowns().addCooldown(stack, DURATION);
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, DURATION, 4));
        ctx.scheduler().timeout(() -> ctx.removeSpecialItem(player, this), DURATION);

        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.5f, 2f);

        return InteractionResult.SUCCESS_SERVER;
    }
}
