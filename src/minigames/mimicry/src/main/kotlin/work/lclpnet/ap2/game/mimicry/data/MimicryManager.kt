package work.lclpnet.ap2.game.mimicry.data

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.*

class MimicryManager(
    private val gameHandle: MiniGameHandle,
    private val rooms: Map<UUID, MimicryRoom>,
    private val buttons: BlockBox,
    private val random: Random,
    private val world: ServerLevel,
    private val completeCallback: (ServerPlayer) -> Unit
) {
    private val sequence = IntArrayList()
    private val progress = Object2IntOpenHashMap<UUID>()
    private val buttonPitches = FloatArray(buttons.volume()) { i -> SoundHelper.getPitch(i % 25) }
    var replay = false
    private val deactivation = HashMap<UUID, TaskHandle>()

    fun eachParticipant(action: (ServerPlayer, MimicryRoom) -> Unit) {
        val participants = gameHandle.participants
        val playerManager = gameHandle.server.playerList

        rooms.forEach { (uuid, room) ->
            if (!participants.isParticipating(uuid)) return@forEach

            val player = playerManager.getPlayer(uuid) ?: return@forEach

            action(player, room)
        }
    }

    fun extendSequence() {
        val buttonCount = buttonCount()

        if (buttonCount <= 0) {
            throw IllegalStateException("There are no buttons")
        }

        sequence.add(random.nextInt(buttonCount))
    }

    fun sequenceLength() = sequence.size

    fun sequenceItem(i: Int) = sequence.getInt(i)

    fun buttonCount() = buttons.volume()

    fun onInputButton(player: ServerPlayer, pos: BlockPos): Boolean {
        if (!replay) return false

        val uuid = player.uuid
        val room = rooms[uuid] ?: return false

        val button = room.buttonIndex(pos)

        if (button == -1) return false

        val offset = progress.getOrDefault(uuid, 0)

        if (offset >= sequence.size) return false

        val expected = sequence.getInt(offset)

        if (expected != button) {
            return true
        }

        val newOffset = offset + 1
        progress.put(uuid, newOffset)

        val pitch = getButtonPitch(button)
        SoundHelper.playSound(player, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS,
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), 0.5f, pitch)

        activateButton(room, button, uuid)

        if (newOffset == sequence.size) {
            onCompleteSequence(player)
        }

        return false
    }

    private fun activateButton(room: MimicryRoom, button: Int, uuid: UUID) {
        room.setButtonActive(button, world)

        deactivation[uuid]?.cancel()

        deactivation[uuid] = gameHandle.scheduler.timeout(Runnable { room.resetActiveButton(world) }, 15)
    }

    private fun onCompleteSequence(player: ServerPlayer) {
        val msg = gameHandle.translations.translateText(player, "game.ap2.mimicry.correct")
            .formatted(ChatFormatting.GREEN)

        player.sendSystemMessage(msg)
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.5f)

        completeCallback(player)
    }

    val playersToEliminate: List<ServerPlayer>
        get() {
            val sequenceLength = sequenceLength()
            return gameHandle.participants
                .filter { progress.getOrDefault(it.uuid, 0) < sequenceLength }
        }

    fun reset() {
        progress.clear()
    }

    fun getButtonPitch(button: Int) = buttonPitches[button % buttonPitches.size]
}
