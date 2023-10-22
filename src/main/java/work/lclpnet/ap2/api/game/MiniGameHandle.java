package work.lclpnet.ap2.api.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.WorldFacade;

import java.util.Set;

public interface MiniGameHandle {

    MinecraftServer getServer();

    GameInfo getGameInfo();

    Logger getLogger();

    WorldFacade getWorldFacade();

    MapFacade getMapFacade();

    HookRegistrar getHookRegistrar();

    TaskScheduler getScheduler();

    TranslationService getTranslations();

    void complete(Set<ServerPlayerEntity> winners);
}
