package work.lclpnet.ap2.base;

import net.fabricmc.loader.api.FabricLoader;

public final class ApConstants {

    public static final boolean DEVELOPMENT = FabricLoader.getInstance().isDevelopmentEnvironment();
    public static final String ID = "ap2";
    public static final String MAP_VERSION = "";

    // people
    public static final String PERSON_LCLP = "@person.lclp";
    public static final String PERSON_BOPS = "@person.bops";

    // other
    public static final String SEPARATOR = "===================================";
    public static final String SCOREBOARD_SEPARATOR = "==============";

    private ApConstants() {}
}
