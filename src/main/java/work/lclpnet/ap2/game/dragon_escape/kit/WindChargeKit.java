package work.lclpnet.ap2.game.dragon_escape.kit;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class WindChargeKit extends SingleItemKit {

    public static final String ID = "wind_charge";

    private static final int CHARGES = 4;

    public WindChargeKit(KitHandle handle) {
        super(handle, ID, Items.WIND_CHARGE, CHARGES);
    }

    @Override
    public void init() {
        // TODO only affect self
    }
}
