package work.lclpnet.ap2.impl.util.model;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.base.ArcadeParty;

public class Models {

    public static final Identifier CROSS = id("cross");

    public static Identifier id(String name) {
        return ArcadeParty.identifier("model/" + name);
    }
}
