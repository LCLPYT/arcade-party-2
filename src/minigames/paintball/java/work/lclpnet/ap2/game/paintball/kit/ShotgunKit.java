package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.core.RegistryAccess;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public ItemStack createItemStack(RegistryAccess manager) {
        ItemStack stack = ItemHelper.getLeatherArmor(getItem(), 0x1fd122);

        configureItemStack(stack);

        return stack;
    }

    private static PaintGun paintGun() {
        return new PaintGun(
                ID, 26, 7, 9.0, 14, 14, 3,
                new PaintGun.SoundCfg(SoundEvents.CHICKEN_EGG, 0.3f, 0.5f),
                new PaintGun.BulletSettings(
                        0.15, 25, 16, 1.5, 0.05f, 4f, 5,
                        2.0f, 0.1f,
                        new PaintGun.BulletSplit(10, 2, 1.3f, 0)
                )
        );
    }
}
