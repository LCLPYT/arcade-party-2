package work.lclpnet.ap2.base;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.kibu.cmd.impl.CommandStack;
import work.lclpnet.kibu.hook.HookStack;
import work.lclpnet.kibu.scheduler.util.SchedulerStack;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.api.WorldFacade;

public record ApContainer(MinecraftServer server, Logger logger, Translations translations, HookStack hookStack,
                          CommandStack commandStack, SchedulerStack schedulerStack, WorldFacade worldFacade,
                          MapFacade mapFacade, PlayerUtil playerUtil, MiniGameManager miniGames,
                          SongManager songManager, DataManager dataManager) {

}
