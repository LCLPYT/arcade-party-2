package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.game.paintball.util.PaintGun;
import work.lclpnet.ap2.game.paintball.util.PaintGunManager;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;

import java.util.Optional;

public class PaintGunKit extends SingleItemKit {

    private final PaintGun paintGun;
    private final PaintGunManager paintGunManager;

    protected PaintGunKit(KitHandle handle, String id, Item item, int count,
                          PaintGun paintGun, PaintGunManager paintGunManager) {
        super(handle, id, item, count);
        this.paintGun = paintGun;
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

            paintGunManager.shoot(player, paintGun, stack);

            return ActionResult.SUCCESS;
        });
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager manager) {
        ItemStack stack = super.createItemStack(manager);

        configureItemStack(stack);

        return stack;
    }

    protected void configureItemStack(ItemStack stack) {
        var group = Optional.of(ArcadeParty.identifier(paintGun.id()));
        stack.set(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(paintGun.cooldownTicks(), group));
        stack.set(DataComponentTypes.MAX_DAMAGE, paintGun.ammo());
    }
}
