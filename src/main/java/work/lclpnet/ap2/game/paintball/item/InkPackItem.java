package work.lclpnet.ap2.game.paintball.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.game.paintball.util.PaintGunManager;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;

public class InkPackItem implements SpecialItem {

    private final PaintGunManager paintGunManager;

    public InkPackItem(PaintGunManager paintGunManager) {
        this.paintGunManager = paintGunManager;
    }

    @Override
    public String id() {
        return "ink_pack";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.INK_SAC);
    }

    @Override
    public boolean shouldTransferToInventory(ServerPlayerEntity player) {
        return false;
    }

    @Override
    public void onPickedUp(ServerPlayerEntity player, ItemStack stack, SpecialItemContext ctx) {
        paintGunManager.refillPaintGun(player);
    }
}
