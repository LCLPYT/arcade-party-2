package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.*

/**
 * Simple controller of a custom objective, providing networking bindings for higher level scoreboard APIs.
 */
class CustomObjective(
    val name: String,
    private var title: Component,
    renderType: ObjectiveCriteria.RenderType,
    numberFormat: NumberFormat?
) {
    val vanillaObjective: Objective = Objective(
        Scoreboard(),
        name,
        ObjectiveCriteria.DUMMY,
        title,
        renderType,
        false,
        numberFormat
    )
    private val entries = HashMap<String, CustomScoreboardEntry>()

    fun display(): Component {
        return title
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || other.javaClass != this.javaClass) return false
        val that = other as CustomObjective
        return this.name == that.name &&
                this.title == that.title
    }

    override fun hashCode(): Int {
        return Objects.hash(name, title)
    }

    override fun toString(): String =
        "Objective[name=$name, title=$title]"

    fun setTitle(title: Component) {
        this.title = title
    }

    fun setEntry(holder: String, entry: CustomScoreboardEntry) {
        entries[holder] = entry
    }

    fun getEntry(holder: String): CustomScoreboardEntry? =
        entries[holder]

    fun add(player: ServerPlayer) {
        val packet = ClientboundSetObjectivePacket(vanillaObjective, ClientboundSetObjectivePacket.METHOD_ADD)
        player.connection.send(packet)
    }

    fun remove(player: ServerPlayer) {
        val packet = ClientboundSetObjectivePacket(vanillaObjective, ClientboundSetObjectivePacket.METHOD_REMOVE)
        player.connection.send(packet)
    }

    fun update(player: ServerPlayer) {
        val packet = ClientboundSetObjectivePacket(vanillaObjective, ClientboundSetObjectivePacket.METHOD_CHANGE)
        player.connection.send(packet)
    }

    fun sendScore(player: ServerPlayer, scoreHolder: String, score: Int, display: Component?, format: NumberFormat?) {
        val packet = ClientboundSetScorePacket(
            scoreHolder,
            name,
            score,
            Optional.ofNullable(display),
            Optional.ofNullable(format)
        )

        player.connection.send(packet)
    }

    fun syncScore(player: ServerPlayer, holder: String) {
        val entry = entries.getOrDefault(holder, null) ?: return

        sendScore(player, holder, entry.score, entry.display, entry.numberFormat)
    }

    fun syncScores(player: ServerPlayer) {
        for (holder in entries.keys) {
            syncScore(player, holder)
        }
    }

    fun setDisplay(player: ServerPlayer, slot: DisplaySlot) {
        setDisplay(player, this, slot)
    }

    fun remove(holder: String) {
        entries.remove(holder)
    }

    fun clear(player: ServerPlayer, holder: String) {
        player.connection.send(ClientboundResetScorePacket(holder, vanillaObjective.name))
    }

    companion object {
        fun setDisplay(player: ServerPlayer, objective: CustomObjective?, slot: DisplaySlot) {
            // could be that ScoreboardObjectiveUpdateS2CPacket with ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE has to be sent
            val packet = ClientboundSetDisplayObjectivePacket(
                slot,
                objective?.vanillaObjective
            )

            player.connection.send(packet)
        }
    }
}
