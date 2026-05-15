package work.lclpnet.ap2.game.panda_finder

import net.minecraft.server.level.ServerPlayer
import java.util.UUID

private const val RESET_MILLIS = 2000
private const val MAX_CLICKS = 3

class SpamManager {

    private val records = HashMap<UUID, Record>()

    fun interact(player: ServerPlayer): Boolean =
        records.getOrPut(player.uuid) { Record() }.interact()
}

private class Record {
    private var lastInteraction = 0L
    private var count = 0

    fun interact(): Boolean {
        val before = lastInteraction
        lastInteraction = System.currentTimeMillis()

        if (lastInteraction - before >= RESET_MILLIS) {
            count = 1
            return false
        }

        if (++count < MAX_CLICKS) return false

        count = 0
        return true
    }
}
