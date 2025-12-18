package work.lclpnet.ap2.game.paintball.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.INK_SAC);
    }

    @Override
    public boolean shouldTransferToInventory(ServerPlayer player) {
        return false;
    }

    @Override
    public void onPickedUp(ServerPlayer player, ItemStack stack, SpecialItemContext ctx) {
        paintGunManager.refillPaintGun(player);
    }
}
