package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import java.util.function.Function

/**
 * One vanilla objective for each player.
 */
open class DynamicScoreboardObjective(
    private val name: String,
    private val renderType: ObjectiveCriteria.RenderType,
    private val title: Function<ServerPlayer, Component>,
    private val playerManager: PlayerList,
) : CustomScoreboardObjective, InformativeScoreboard, VirtualScoreboardObjective {

    private val objectives = HashMap<UUID, CustomObjective>()
    private val layout = ScoreboardLayout()
    private val entries = HashMap<String, DynamicEntry>()

    private var slot = DisplaySlot.SIDEBAR
    var defaultNumberFormat: NumberFormat = StyledFormat.SIDEBAR_DEFAULT
    var defaultDisplay: (ServerPlayer, String) -> Component = { _, holder ->
        Component.literal(holder)
    }

    override fun add(player: ServerPlayer) {
        val objective = getOrCreateObjective(player)

        for (dynamicEntry in entries.values) {
            dynamicEntry.put(player, objective)
        }

        objective.add(player)
        objective.setDisplay(player, slot)
        objective.syncScores(player)
    }

    private fun getOrCreateObjective(player: ServerPlayer): CustomObjective =
        objectives.computeIfAbsent(player.getUUID()) { _ ->
            createObjective(player)
        }

    override fun remove(player: ServerPlayer) {
        val objective = objectives.remove(player.getUUID()) ?: return

        CustomObjective.setDisplay(player, null, slot)
        objective.remove(player)
    }

    override fun update(player: ServerPlayer) {
        if (!objectives.containsKey(player.getUUID())) return

        remove(player)
        add(player)
    }

    protected fun createObjective(player: ServerPlayer): CustomObjective {
        val objectiveName = "${name}_${player.scoreboardName}"
        val display = title.apply(player)

        return CustomObjective(objectiveName, display, renderType, StyledFormat.SIDEBAR_DEFAULT)
    }

    fun setSlot(slot: DisplaySlot) {
        if (slot == this.slot) return

        val prevSlot = this.slot
        this.slot = slot

        eachObjective { player, objective ->
            CustomObjective.setDisplay(player, null, prevSlot)
            objective.setDisplay(player, slot)
        }
    }

    override fun setScore(scoreHolder: String, score: Int) {
        val entry = getOrCreateEntry(scoreHolder)

        entry.defaultScore = score

        entry.eachPlayerEntry {
            it.score = score
        }

        modifyEntry(scoreHolder) {
            it.score = score
        }
    }

    fun setScore(player: ServerPlayer, scoreHolder: String, score: Int) {
        val entry = getOrCreateEntry(scoreHolder)

        entry.getOrCreatePlayerEntry(player).score = score

        modifyEntry(player, scoreHolder) {
            it.score = score
        }
    }

    override fun setDisplayName(scoreHolder: String, display: Component?) {
        val entry = getOrCreateEntry(scoreHolder)

        entry.displayName = { _ ->
            display
        }

        entry.eachPlayerEntry {
            it.display = display
        }

        modifyEntry(scoreHolder) {
            it.display = display
        }
    }

    fun setDisplayName(player: ServerPlayer, scoreHolder: String, display: Component?) {
        val entry = getOrCreateEntry(scoreHolder)

        entry.getOrCreatePlayerEntry(player).display = display

        modifyEntry(player, scoreHolder) {
            it.display = display
        }
    }

    override fun setNumberFormat(scoreHolder: String, numberFormat: NumberFormat?) {
        val entry = getOrCreateEntry(scoreHolder)
        entry.defaultNumberFormat = numberFormat

        entry.eachPlayerEntry {
            it.numberFormat = numberFormat
        }

        modifyEntry(scoreHolder) {
            it.numberFormat = numberFormat
        }
    }

    fun setNumberFormat(player: ServerPlayer, scoreHolder: String, numberFormat: NumberFormat?) {
        val entry = getOrCreateEntry(scoreHolder)

        entry.getOrCreatePlayerEntry(player).numberFormat = numberFormat

        modifyEntry(player, scoreHolder) {
            it.numberFormat = numberFormat
        }
    }

    override fun removeEntry(scoreHolder: String) {
        entries.remove(scoreHolder)

        eachObjective { player, objective ->
            objective.remove(scoreHolder)
            objective.clear(player, scoreHolder)
        }
    }

    private fun getOrCreateEntry(holder: String): DynamicEntry {
        var entry = entries.getOrDefault(holder, null)

        if (entry != null) {
            return entry
        }

        entry = DynamicEntry(holder, 0, defaultNumberFormat) { player ->
            defaultDisplay(player, holder)
        }

        setDynamicEntry(holder, entry)

        return entry
    }

    private fun setDynamicEntry(holder: String, entry: DynamicEntry) {
        entries[holder] = entry

        eachObjective { player, objective ->
            entry.put(player, objective)
        }
    }

    override fun createText(text: Component, position: Int): ScoreHandle {
        return createText({ `_`: ServerPlayer? -> text }, position)
    }

    override fun createText(text: TranslatedText, position: Int): ScoreHandle {
        return createText({ player -> text.translateFor(player) }, position)
    }

    fun createText(textFactory: (ServerPlayer) -> Component, position: Int): ScoreHandle {
        val holder = UUID.randomUUID().toString()
        val score = layout.resolvePosition(position)

        val entry = DynamicEntry(
            holder,
            score,
            BlankFormat.INSTANCE,
            textFactory
        )

        setDynamicEntry(holder, entry)

        return ScoreHandle(holder, this)
    }

    fun createDynamicText(line: TranslatedText, position: Int): DynamicScoreHandle {
        return createDynamicText({ player -> line.translateFor(player) }, position)
    }

    fun createDynamicText(textFactory: (ServerPlayer) -> Component, position: Int): DynamicScoreHandle {
        val holder = UUID.randomUUID().toString()
        val score = layout.resolvePosition(position)

        val entry = DynamicEntry(
            holder,
            score,
            BlankFormat.INSTANCE,
            textFactory
        )

        setDynamicEntry(holder, entry)

        return DynamicScoreHandle(holder, this)
    }

    protected fun modifyEntry(
        scoreHolder: String,
        action: (CustomScoreboardEntry) -> Unit,
    ) {
        eachObjective { objective ->
            objective.getEntry(scoreHolder)?.let { action(it) }
        }

        eachObjective { player, objective ->
            objective.syncScore(
                player,
                scoreHolder
            )
        }
    }

    protected fun eachObjective(action: (ServerPlayer, CustomObjective) -> Unit) {
        for ((uuid, objective) in objectives) {
            val player = playerManager.getPlayer(uuid)

            if (player != null) {
                action(player, objective)
            }
        }
    }

    protected fun eachObjective(action: (CustomObjective) -> Unit) {
        for (objective in objectives.values) {
            action(objective)
        }
    }

    protected fun modifyEntry(
        player: ServerPlayer,
        scoreHolder: String,
        action: (CustomScoreboardEntry) -> Unit,
    ) {
        val objective = getOrCreateObjective(player)

        val customEntry = objective.getEntry(scoreHolder) ?: run {
            val dynamicEntry = getOrCreateEntry(scoreHolder)
            val entry = dynamicEntry.getOrCreatePlayerEntry(player).createEntry()

            objective.setEntry(scoreHolder, entry)
            entry
        }

        action(customEntry)

        objective.syncScore(player, scoreHolder)
    }

    override fun unload() {
        eachObjective { player, objective ->
            CustomObjective.setDisplay(player, null, slot)
            objective.remove(player)
        }

        objectives.clear()
    }

    private class DynamicEntry(
        private val holder: String,
        var defaultScore: Int,
        var defaultNumberFormat: NumberFormat?,
        var displayName: (ServerPlayer) -> Component?,
    ) {
        private val playerEntries = HashMap<UUID, DynamicPlayerEntry>()

        fun put(player: ServerPlayer, objective: CustomObjective) {
            objective.setEntry(holder, createEntry(player))
        }

        fun createEntry(player: ServerPlayer): CustomScoreboardEntry {
            return getOrCreatePlayerEntry(player).createEntry()
        }

        fun getOrCreatePlayerEntry(player: ServerPlayer): DynamicPlayerEntry {
            return playerEntries.computeIfAbsent(player.uuid) { _ ->
                val wrapper = DynamicPlayerEntry()
                wrapper.display = displayName(player)
                wrapper.score = defaultScore
                wrapper.numberFormat = defaultNumberFormat
                wrapper
            }
        }

        fun eachPlayerEntry(action: (DynamicPlayerEntry) -> Unit) {
            for (entry in playerEntries.values) {
                action(entry)
            }
        }
    }

    private class DynamicPlayerEntry {
        var score = 0
        var numberFormat: NumberFormat? = null
        var display: Component? = null

        fun createEntry(): CustomScoreboardEntry =
            CustomScoreboardEntry(display, numberFormat, score)
    }
}
