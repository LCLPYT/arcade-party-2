package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import work.lclpnet.ap2.game.paintball.util.PaintGun;
import work.lclpnet.ap2.game.paintball.util.PaintGunManager;
import work.lclpnet.ap2.impl.game.kit.KitHandle;

public class SniperKit extends PaintGunKit {

    public static final String ID = "sniper";

    public SniperKit(KitHandle handle, PaintGunManager paintGunManager) {
        super(handle, ID, Items.DIAMOND_HORSE_ARMOR, 1, paintGun(), paintGunManager);
    }

    private static PaintGun paintGun() {
        return new PaintGun(
                ID, 28, 1, 0, 8, 17, 2,
                new PaintGun.SoundCfg(SoundEvents.ITEM_MACE_SMASH_AIR, 0.4f, 1f),
                new PaintGun.BulletSettings(
                        0.3, 100, 1, 1.0, 0.01f, 19.5f, 4,
                        2.9f, 0.1f,
                        new PaintGun.BulletSplit(0, 12, 1.48f, 1)
                )
        );
    }
}
