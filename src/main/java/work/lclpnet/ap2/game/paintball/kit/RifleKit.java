package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.item.Items;
import work.lclpnet.ap2.game.paintball.util.PaintGun;
import work.lclpnet.ap2.game.paintball.util.PaintGunManager;
import work.lclpnet.ap2.impl.game.kit.KitHandle;

public class RifleKit extends PaintGunKit {

    public static final String ID = "rifle";

    public RifleKit(KitHandle handle, PaintGunManager paintGunManager) {
        super(handle, ID, Items.IRON_HORSE_ARMOR, 1, paintGun(), paintGunManager);
    }

    private static PaintGun paintGun() {
        return new PaintGun(
                ID, 3, 1, 0,
                new PaintGun.BulletSettings(
                        0.2, 16, 12, 2.0, 0.1f, 3.0f, 8,
                        1.5f, 5, 6, 1.1f
                )
        );
    }
}
