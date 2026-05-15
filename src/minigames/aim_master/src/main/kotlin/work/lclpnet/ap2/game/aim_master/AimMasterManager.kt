package work.lclpnet.ap2.game.aim_master

import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class AimMasterManager(
    val domains: Map<UUID, AimMasterDomain>,
    private val sequence: AimMasterSequence
) {
    private val playerProgress = HashMap<UUID, Int>()

    fun advancePlayer(player: ServerPlayer) {
        val uuid = player.uuid
        val domain = domains[uuid] ?: return
        val progress = getPlayerProgress(player)

        domain.removeBlocks(sequence.items[progress])
        domain.setBlocks(sequence.items[progress + 1], player)
        playerProgress[uuid] = progress + 1
    }

    fun getPlayerProgress(player: ServerPlayer): Int = playerProgress.getOrDefault(player.uuid, 0)
}
