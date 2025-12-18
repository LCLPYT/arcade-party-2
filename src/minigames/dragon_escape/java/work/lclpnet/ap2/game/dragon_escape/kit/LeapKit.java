package work.lclpnet.ap2.game.dragon_escape.kit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.KitOptions;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.scheduler.Ticks;

public class LeapKit extends SingleItemKit {

    public static final String ID = "leap";

    private static final Item ITEM = Items.IRON_AXE;
    private static final int
            USES = 3,
            COOLDOWN_TICKS = Ticks.seconds(3);
    private static final double LEAP_STRENGTH = 1.8;

    public LeapKit(KitHandle handle) {
        super(handle, ID, ITEM, USES);
    }

    @Override
    public void init(KitOptions options) {
        handle.hooks().registerHook(PlayerInteractionHooks.USE_ITEM, (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayer player)) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);

            if (stack.is(ITEM) && !player.getCooldowns().isOnCooldown(stack)) {
                useItem(player, stack);
                return InteractionResult.SUCCESS_SERVER;
            }

            return InteractionResult.PASS;
        });
    }

    private void useItem(ServerPlayer player, ItemStack stack) {
        stack.consume(1, player);

        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);

        VelocityModifier.setVelocity(player, player.getLookAngle().scale(LEAP_STRENGTH));

        ServerLevel world = player.level();

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5f, 1.8f);

        world.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 25,
                0.2, 0.5, 0.2, 0.2);
    }
}
