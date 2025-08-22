package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
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
                ID, 3, 1, 0.5, 70, 2, 4,
                new PaintGun.SoundCfg(SoundEvents.ENTITY_ITEM_PICKUP, 0.2f, 2f),
                new PaintGun.BulletSettings(
                        0.2, 25, 12, 2.0, 0.1f, 3.0f, 4,
                        1.5f, 0.75f,
                        new PaintGun.BulletSplit(4, 6, 1.1f, 0)
                )
        );
    }
}
