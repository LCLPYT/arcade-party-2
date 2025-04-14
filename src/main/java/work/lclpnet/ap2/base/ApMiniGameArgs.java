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

/**
 * A container of objects required for starting a mini-game.
 * All objects in this class should be required in the scope of a mini-game, e.g. {@link Translations},
 * {@link HookStack}, {@link SchedulerStack}, {@link WorldFacade} etc.
 * Other stuff that is required for the "base" game cycle, so outside the mini-game scope,
 * should rather go into the {@link work.lclpnet.ap2.base.util.ApBaseArgs} container.
 */
public record ApMiniGameArgs(
        MinecraftServer server, Logger logger, Translations translations, HookStack hookStack,
        CommandStack commandStack, SchedulerStack schedulerStack, WorldFacade worldFacade, MapFacade mapFacade,
        PlayerUtil playerUtil, MiniGameManager miniGames, SongManager songManager, DataManager dataManager
) {}
