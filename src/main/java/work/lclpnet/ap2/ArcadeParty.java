package work.lclpnet.ap2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.plugin.ext.KibuPlugin;
import work.lclpnet.mplugins.ext.WorldStateListener;

public class ArcadeParty extends KibuPlugin implements WorldStateListener {

    public static final String ID = "ap2";
    private static final Logger logger = LoggerFactory.getLogger(ID);

    @Override
    public void loadKibuPlugin() {
        logger.info("ArcadeParty2 initialized.");
    }

    @Override
    public void onWorldReady() {
        // called when the main world is loaded
    }

    @Override
    public void onWorldUnready() {
        // called when the main world or the plugin is unloading
    }
}