package work.lclpnet.ap2.util.scoreboard

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import work.lclpnet.ap2.util.StyleTransformer
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TextTranslatable
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*

/**
 * One vanilla objective for each language.
 */
class TranslatedScoreboardObjective(
    private val translations: Translations,
    private val playerManager: PlayerList,
    private val name: String,
    private val renderType: ObjectiveCriteria.RenderType,
    private var translationKey: String,
    private var args: Array<Any>
) : CustomScoreboardObjective,
    StyleTransformer<TranslatedScoreboardObjective>,
    InformativeScoreboard,
    VirtualScoreboardObjective {

    private val objectivePlayers = HashMap<CustomObjective, MutableSet<UUID>>()
    private val localizedObjectives = HashMap<String, CustomObjective>()
    private val players = HashMap<UUID, String>()
    private val scores = Object2IntOpenHashMap<String>()
    private val entries = HashMap<String, CustomEntry>()
    private val layout = ScoreboardLayout()

    var defaultEntry = CustomEntry(
        null,
        null,
        TranslatedNumberFormat.constant(StyledFormat.SIDEBAR_DEFAULT)
    )
        private set

    var slot = DisplaySlot.SIDEBAR
        private set

    override var style = Style.EMPTY

    private var displayFunction: ((String) -> Component?)? = null

    override fun add(player: ServerPlayer) {
        val language = translations.getLanguage(player)
        val uuid = player.getUUID()

        val oldLanguage = players[uuid]

        // check if language did change
        if (language == oldLanguage) return

        if (oldLanguage != null) {
            // the language changed, remove the player from the old boss bar
            remove(player)
        }

        val objective = getLocalizedObjective(language)

        objectivePlayers.computeIfAbsent(objective) { _ ->
            HashSet<UUID>()
        }.add(uuid)

        objective.add(player)
        objective.setDisplay(player, slot)
        syncScores(objective, player)

        players[uuid] = language
    }

    override fun remove(player: ServerPlayer) {
        val uuid = player.getUUID()
        val lang = players.remove(uuid) ?: return

        val objective = localizedObjectives[lang] ?: return

        CustomObjective.setDisplay(player, null, slot)
        objective.remove(player)

        objectivePlayers[objective]?.remove(uuid)
    }

    override fun update(player: ServerPlayer) {
        if (!players.containsKey(player.getUUID())) return

        // adding the player will update the language
        add(player)
    }

    private fun getLocalizedObjective(language: String): CustomObjective =
        localizedObjectives.computeIfAbsent(language) { language ->
            createLocalizedObjective(language)
        }

    private fun createLocalizedObjective(language: String): CustomObjective {
        val localizedTitle = getLocalizedTitle(language)

        val suffix = language.replace("[^a-zA-Z0-9._-]".toRegex(), "") // remove invalid characters
        val localizedName = name + "_" + (suffix)

        return CustomObjective(
            localizedName,
            localizedTitle,
            renderType,
            defaultEntry.numberFormat?.translateTo(language)
        )
    }

    private fun getLocalizedTitle(language: String): Component {
        val rootText = translations.translateText(language, translationKey, *args)

        rootText.setStyle(style)

        return rootText
    }

    fun setTitle(translationKey: String, vararg args: Any) {
        this.translationKey = translationKey
        this.args = arrayOf(*args)

        for (entry in localizedObjectives.entries) {
            val localizedTitle = getLocalizedTitle(entry.key)

            val objective = entry.value
            objective.setTitle(localizedTitle)

            val uuids = objectivePlayers[objective] ?: emptySet()

            for (uuid in uuids) {
                val player = playerManager.getPlayer(uuid) ?: continue

                objective.update(player)
            }
        }
    }

    private fun updateObjectives(action: (CustomObjective) -> Unit) {
        for (objective in localizedObjectives.values) {
            action(objective)
        }
    }

    fun setSlot(slot: DisplaySlot) {
        if (slot == this.slot) return

        val prevSlot = this.slot
        this.slot = slot

        for (entry in players.entries) {
            val player = playerManager.getPlayer(entry.key) ?: continue

            CustomObjective.setDisplay(player, null, prevSlot)

            val lang = entry.value
            val objective = localizedObjectives[lang] ?: continue

            CustomObjective.setDisplay(player, objective, slot)
        }
    }

    fun getScore(scoreHolder: String?): Int =
        scores.getOrDefault(scoreHolder, 0)

    override fun setScore(scoreHolder: String, score: Int) {
        scores.put(scoreHolder, score)

        updateObjectives { objective ->
            syncScore(objective, scoreHolder, score)
        }
    }

    override fun setDisplayName(scoreHolder: String, display: Component?) {
        val entry = getEntry(scoreHolder)
        entries[scoreHolder] = entry.copy(display = display, translatedDisplay = null)
        syncEntry(scoreHolder)
    }

    fun setDisplayName(scoreHolder: String, display: TextTranslatable?) {
        val entry = getEntry(scoreHolder)
        entries[scoreHolder] = entry.copy(display = null, translatedDisplay = display)
        syncEntry(scoreHolder)
    }

    override fun setNumberFormat(scoreHolder: String, numberFormat: NumberFormat?) {
        val entry = getEntry(scoreHolder)
        entries[scoreHolder] = entry.copy(numberFormat = TranslatedNumberFormat.constant(numberFormat))
        syncEntry(scoreHolder)
    }

    fun setNumberFormat(scoreHolder: String, numberFormat: TranslatedNumberFormat?) {
        val entry = getEntry(scoreHolder)
        entries[scoreHolder] = entry.copy(numberFormat = numberFormat)
        syncEntry(scoreHolder)
    }

    fun setDisplayName(displayFunction: ((String) -> Component?)?) {
        this.displayFunction = displayFunction
    }

    fun setNumberFormat(numberFormat: NumberFormat?) {
        defaultEntry = defaultEntry.copy(numberFormat = TranslatedNumberFormat.constant(numberFormat))
    }

    private fun getEntry(scoreHolder: String): CustomEntry {
        if (displayFunction == null) {
            return entries.getOrDefault(scoreHolder, defaultEntry)
        }

        return entries.computeIfAbsent(scoreHolder) { _ ->
            val display = displayFunction!!(scoreHolder)
            defaultEntry.copy(display = display, translatedDisplay = null)
        }
    }

    private fun syncEntry(scoreHolder: String) {
        val score = getScore(scoreHolder)

        updateObjectives { objective ->
            syncScore(objective, scoreHolder, score)
        }
    }

    private fun syncScores(objective: CustomObjective, player: ServerPlayer) {
        for ((scoreHolder, score) in scores) {
            val entry = getEntry(scoreHolder)
            val display = getScoreHolderDisplay(entry, player)
            val format = entry.numberFormat?.translateTo(translations.getLanguage(player))

            objective.sendScore(player, scoreHolder, score, display, format)
        }
    }

    private fun syncScore(objective: CustomObjective, scoreHolder: String, score: Int) {
        val uuids = objectivePlayers[objective] ?: return

        val entry = getEntry(scoreHolder)

        for (uuid in uuids) {
            val player = playerManager.getPlayer(uuid) ?: continue

            val display = getScoreHolderDisplay(entry, player)
            val format = entry.numberFormat?.translateTo(translations.getLanguage(player))

            objective.sendScore(player, scoreHolder, score, display, format)
        }
    }

    private fun getScoreHolderDisplay(entry: CustomEntry, viewer: ServerPlayer): Component? {
        val translatedDisplay = entry.translatedDisplay ?: return entry.display

        val language = translations.getLanguage(viewer)

        return translatedDisplay.translateTo(language)
    }

    override fun createText(text: Component, position: Int): ScoreHandle {
        val handle = createHandle(position)
        handle.setDisplay(text)

        return handle
    }

    override fun createText(text: TranslatedText, position: Int): ScoreHandle {
        val handle = createHandle(position)

        setDisplayName(handle.holder, text)

        return handle
    }

    override fun removeEntry(scoreHolder: String) {
        scores.removeInt(scoreHolder)
        entries.remove(scoreHolder)

        updateObjectives { objective ->
            objective.remove(scoreHolder)

            val uuids = objectivePlayers[objective] ?: emptySet()

            for (uuid in uuids) {
                val player = playerManager.getPlayer(uuid) ?: continue

                objective.clear(player, scoreHolder)
            }
        }
    }

    private fun createHandle(position: Int): ScoreHandle {
        val holder = UUID.randomUUID().toString()
        val handle = ScoreHandle(holder, this)

        setScore(holder, layout.resolvePosition(position))

        handle.setNumberFormat(BlankFormat.INSTANCE)

        return handle
    }

    override fun unload() {
        for ((objective, uuids) in objectivePlayers) {
            for (uuid in uuids) {
                val player = playerManager.getPlayer(uuid) ?: continue

                objective.remove(player)
            }
        }
    }

    data class CustomEntry(
        val display: Component?,
        val translatedDisplay: TextTranslatable?,
        val numberFormat: TranslatedNumberFormat?
    )
}
