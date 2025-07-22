package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import work.lclpnet.ap2.game.paintball.util.PaintGunManager;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;

public class RifleKit extends SingleItemKit {

    public static final String ID = "rifle";

    private final PaintGunManager paintGunManager;

    public RifleKit(KitHandle handle, PaintGunManager paintGunManager) {
        super(handle, ID, Items.IRON_HORSE_ARMOR, 1);
        this.paintGunManager = paintGunManager;
    }

    @Override
    public void init() {
        handle.hooks().registerHook(PlayerInteractionHooks.USE_ITEM, (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayerEntity player)) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);

            if (!stack.isOf(getItem())) {
                return ActionResult.PASS;
            }

            paintGunManager.shoot(player);

            return ActionResult.SUCCESS;
        });
    }
}
