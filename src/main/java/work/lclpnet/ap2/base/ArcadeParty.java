package work.lclpnet.ap2.base;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.plugin.ext.KibuPlugin;

public class ArcadeParty extends KibuPlugin {

    public static final Logger logger = LoggerFactory.getLogger(ApConstants.ID);
    private static ArcadeParty instance;

    @Override
    public void loadKibuPlugin() {
        instance = this;

        logger.info("ArcadeParty2 initialized.");
    }

    @NotNull
    public static ArcadeParty getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Called to early");
        }

        return instance;
    }

    public static Identifier identifier(String path) {
        return new Identifier(ApConstants.ID, path);
    }
}