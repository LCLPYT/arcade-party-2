package work.lclpnet.ap2.game.maniac_digger

import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.maniac_digger.data.MdGenerator
import work.lclpnet.ap2.game.maniac_digger.data.MdPipe
import work.lclpnet.ap2.game.openRandomMap
import java.util.*

class ManiacDiggerFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = openRandomMap(handle)

        val winHeight = map.requireProperty<Number>("goal-height").toInt()

        val generated = MdGenerator(level, map, handle.logger, Random())
            .generate(handle.participants.count())

        val pipes = HashMap<UUID, MdPipe>()
        var i = 0

        for (player in handle.participants) {
            pipes[player.uuid] = generated[i++]
        }

        return ManiacDiggerInstance(handle, level, map, winHeight, pipes)
    }
}
