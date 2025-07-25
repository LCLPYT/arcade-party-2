package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.item.Items;
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
                ID, 33, 1, 0,
                new PaintGun.BulletSettings(
                        0.3, 100, 1, 1.0, 0.01f, 19.5f, 4,
                        2.1f, 0.55f,
                        new PaintGun.BulletSplit(0, 8, 2.0f, 0.5f)
                )
        );
    }
}
