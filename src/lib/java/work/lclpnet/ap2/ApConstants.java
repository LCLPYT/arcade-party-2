package work.lclpnet.ap2;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApConstants {

    public static final boolean
            DEVELOPMENT = FabricLoader.getInstance().isDevelopmentEnvironment() || "true".equals(System.getenv("AP2_DEV")),
            DEBUG = FabricLoader.getInstance().isDevelopmentEnvironment();

    public static final String ID = "ap2";

    public static final Logger logger = LoggerFactory.getLogger(ID);

    // people
    public static final String PERSON_LCLP = "@person.lclp";
    public static final String PERSON_BOPS = "@person.bops";

    // other
    public static final String SEPARATOR = "===================================";
    public static final String SCOREBOARD_SEPARATOR = "==============";
    public static final String SCOREBOARD_SEPARATOR_SM = "----------------";

    private ApConstants() {}

    public static Identifier identifier(String path) {
        return Identifier.of(ID, path);
    }
}
