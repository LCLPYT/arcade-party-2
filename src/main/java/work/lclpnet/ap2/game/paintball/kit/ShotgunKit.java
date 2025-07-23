package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import work.lclpnet.ap2.game.paintball.util.PaintGun;
import work.lclpnet.ap2.game.paintball.util.PaintGunManager;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.util.ItemHelper;

public class ShotgunKit extends PaintGunKit {

    public static final String ID = "shotgun";

    public ShotgunKit(KitHandle handle, PaintGunManager paintGunManager) {
        super(handle, ID, Items.LEATHER_HORSE_ARMOR, 1, paintGun(), paintGunManager);
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager manager) {
        return ItemHelper.getLeatherArmor(getItem(), 0x1fd122);
    }

    private static PaintGun paintGun() {
        return new PaintGun(
                ID, 26, 7, 7.5,
                new PaintGun.BulletSettings(
                        0.15, 18, 16, 2.5, 0.05f, 2.5f, 13,
                        1.55f,
                        new PaintGun.BulletSplit(10, 2, 1.3f, 5)
                )
        );
    }
}
