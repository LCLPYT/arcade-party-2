package work.lclpnet.ap2.game.fine_tuning;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.lobby.game.api.WorldFacade;

import java.util.Optional;
import java.util.function.Consumer;

class StagePhase {

    private final MiniGameHandle gameHandle;
    private final ScoreTimeDataContainer data;
    private final Consumer<Optional<ServerPlayerEntity>> winnerAction;

    public StagePhase(MiniGameHandle gameHandle, ScoreTimeDataContainer data, Consumer<Optional<ServerPlayerEntity>> winnerAction) {
        this.gameHandle = gameHandle;
        this.data = data;
        this.winnerAction = winnerAction;
    }

    public void beginStage() {
        MinecraftServer server = gameHandle.getServer();
        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        playerUtil.setDefaultGameMode(GameMode.ADVENTURE);

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            playerUtil.resetPlayer(player);
            worldFacade.teleport(player);
        }

        winnerAction.accept(data.getBestPlayer(gameHandle.getServer()));
    }
}
