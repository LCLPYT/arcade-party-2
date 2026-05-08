package work.lclpnet.ap2.game.paintball.kit;

import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseCooldown;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.game.paintball.util.PaintGun;
import work.lclpnet.ap2.game.paintball.util.PaintGunManager;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.KitOptions;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;

import java.util.Optional;

public class PaintGunKit extends SingleItemKit {

    @Getter
    private final PaintGun paintGun;
    private final PaintGunManager paintGunManager;

    protected PaintGunKit(KitHandle handle, String id, Item item, int count,
                          PaintGun paintGun, PaintGunManager paintGunManager) {
        super(handle, id, item, count);
        this.paintGun = paintGun;
        this.paintGunManager = paintGunManager;
    }

    @Override
    public void init(KitOptions options) {
        PlayerInteractionHooks.USE_ITEM.registerWith(handle.hooks(), (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayer player)) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);

            if (!stack.is(getItem())) {
                return InteractionResult.PASS;
            }

            paintGunManager.shoot(player, paintGun, stack);

            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public void configureItemStack(ItemStack stack) {
        super.configureItemStack(stack);

        var group = Optional.of(ApConstants.identifier(paintGun.id()));
        stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(paintGun.cooldownTicks(), group));
        stack.set(DataComponents.MAX_DAMAGE, paintGun.ammo());
    }
}
