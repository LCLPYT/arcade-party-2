package work.lclpnet.ap2.base;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.kibu.plugin.hook.HookStack;
import work.lclpnet.kibu.plugin.scheduler.SchedulerStack;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.WorldFacade;

public record ApContainer(MinecraftServer server, Logger logger, TranslationService translationService,
                          HookStack hookStack, SchedulerStack schedulerStack, WorldFacade worldFacade,
                          MapFacade mapFacade, PlayerUtil playerUtil) {

}
