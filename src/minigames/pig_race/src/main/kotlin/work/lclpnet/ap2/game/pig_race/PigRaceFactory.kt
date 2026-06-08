package work.lclpnet.ap2.game.pig_race

import kotlinx.coroutines.future.await
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.impl.music.MusicHelper.ARCADE_PARTY_GAME_TAG

class PigRaceFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val nextRoundSong = handle.songManager
            .getSongAndCache(ARCADE_PARTY_GAME_TAG, NEXT_ROUND_SONG_ID)
            .await()
            .orElse(null)

        return PigRaceInstance(handle, level, map, nextRoundSong)
    }
}
