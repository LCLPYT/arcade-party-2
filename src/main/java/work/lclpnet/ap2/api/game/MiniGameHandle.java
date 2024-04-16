package work.lclpnet.ap2.api.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import work.lclpnet.activity.util.BossBarHandler;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.game.team.TeamConfig;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.util.DeathMessages;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.impl.prot.MutableProtectionConfig;
import work.lclpnet.mplugins.ext.Unloadable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public interface MiniGameHandle {

    MinecraftServer getServer();

    GameInfo getGameInfo();

    Logger getLogger();

    WorldFacade getWorldFacade();

    MapFacade getMapFacade();

    HookRegistrar getHookRegistrar();

    CommandRegistrar getCommandRegistrar();

    /**
     * Get the mini-game root scheduler.
     * This scheduler is stopped when the mini-game terminates and can be used for every important scheduler task.
     * Game-logic related tasks should be scheduled with the game scheduler instead, as it is stopped when someone wins.
     * @return The root scheduler for this mini-game.
     */
    TaskScheduler getScheduler();

    /**
     * Get the game scheduler, which is a child of the game root scheduler (obtained by {@link #getScheduler()}).
     * This scheduler can be reset and is automatically stopped, the moment someone wins the game.
     * In contrast, the game root scheduler is only stopped when the mini-game terminates.
     * @return The game scheduler for game logic.
     */
    TaskScheduler getGameScheduler();

    TranslationService getTranslations();

    Participants getParticipants();

    WorldBorderManager getWorldBorderManager();

    PlayerUtil getPlayerUtil();

    BossBarProvider getBossBarProvider();

    BossBarHandler getBossBarHandler();

    CustomScoreboardManager getScoreboardManager();

    Optional<TeamConfig> getTeamConfig();

    SongManager getSongManager();

    DeathMessages getDeathMessages();

    void resetGameScheduler();

    void protect(Consumer<MutableProtectionConfig> action);

    void closeWhenDone(Unloadable unloadable);

    void complete(Set<ServerPlayerEntity> winners);

    default void complete(ServerPlayerEntity winner) {
        complete(Set.of(winner));
    }

    default void completeWithoutWinner() {
        complete(Set.of());
    }
}
