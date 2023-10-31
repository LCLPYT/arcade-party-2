package work.lclpnet.ap2.api.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.impl.prot.MutableProtectionConfig;

import java.util.Set;
import java.util.function.Consumer;

public interface MiniGameHandle {

    MinecraftServer getServer();

    GameInfo getGameInfo();

    Logger getLogger();

    WorldFacade getWorldFacade();

    MapFacade getMapFacade();

    HookRegistrar getHookRegistrar();

    TaskScheduler getScheduler();

    TranslationService getTranslations();

    Participants getParticipants();

    WorldBorderManager getWorldBorderManager();

    PlayerUtil getPlayerUtil();

    void protect(Consumer<MutableProtectionConfig> action);

    void complete(Set<ServerPlayerEntity> winners);

    default void complete(ServerPlayerEntity winner) {
        complete(Set.of(winner));
    }

    default void completeWithoutWinner() {
        complete(Set.of());
    }
}
