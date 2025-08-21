package work.lclpnet.ap2.host_default.util;

import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.ap2.host_default.ApMiniGameArgs;
import work.lclpnet.ap2.host_default.cmd.ForceGameCommand;
import work.lclpnet.lobby.game.api.GameFinisher;

/**
 * A container for objects required for the arcade-party base game.
 * In contrast to the {@link ApMiniGameArgs}, which contains objects required for the mini-games on a standalone level,
 * this class contains objects required for the actual "base" of arcade-party.
 * The "base" of arcade-party refers to the default type of game cycle, where players collect points earned from mini-games.
 * That means, this class contains stuff like the {@link ScoreManager} that actually manages the accumulation of points,
 * the {@link GameQueue} that holds information about which games are played next and the {@link PlayerManager} that keeps track
 * of which state players are in.
 */
public record ApBaseArgs(
        ApMiniGameArgs miniGameArgs, GameQueue gameQueue, PlayerManager playerManager, ForceGameCommand forceGameCommand,
        SongCache sharedSongCache, ScoreManager scoreManager, GameFinisher finisher
) {}
