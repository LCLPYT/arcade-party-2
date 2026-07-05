package work.lclpnet.ap2.game.maniac_digger

import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.maniac_digger.data.MdGenerator
import work.lclpnet.ap2.game.maniac_digger.data.MdPipe
import work.lclpnet.ap2.game.util.openRandomMap
import java.util.*

class ManiacDiggerFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val winHeight = map.requireProperty<Number>("goal-height").toInt()

        val random = Random()
        val participants = handle.participants.toList()
        val assignment = handle.colorPreferences.assign(participants, random)
        val colors = participants.map { assignment.getValue(it.uuid) }

        val generated = MdGenerator(level, map, handle.logger, random)
            .generate(colors)

        val pipes = HashMap<UUID, MdPipe>()

        for ((i, player) in participants.withIndex()) {
            pipes[player.uuid] = generated[i]
        }

        return ManiacDiggerInstance(handle, level, map, winHeight, pipes)
    }
}
