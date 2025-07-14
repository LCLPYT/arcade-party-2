package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.item.Items;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;

public class RifleKit extends SingleItemKit {

    public static final String ID = "rifle";

    public RifleKit(KitHandle handle) {
        super(handle, ID, Items.IRON_HORSE_ARMOR, 1);
    }


}
