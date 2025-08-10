package work.lclpnet.ap2.impl.activity;

import net.minecraft.server.MinecraftServer;
import work.lclpnet.activity.component.ComponentKey;

public class ArcadePartyComponents {

    public static final ComponentKey<ScoreboardComponent> SCORE_BOARD = context -> {
        MinecraftServer server = context.getServer();
        return new ScoreboardComponent(server.getScoreboard(), server.getPlayerManager());
    };

    private ArcadePartyComponents() {}
}
