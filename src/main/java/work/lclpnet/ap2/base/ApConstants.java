package work.lclpnet.ap2.base;

import net.fabricmc.loader.api.FabricLoader;

public final class ApConstants {

    public static final boolean DEVELOPMENT = FabricLoader.getInstance().isDevelopmentEnvironment();
    public static final String ID = "ap2";

    // people
    public static final String PERSON_LCLP = "LCLP";
    public static final String SEPERATOR = "===================================";

    private ApConstants() {}
}
