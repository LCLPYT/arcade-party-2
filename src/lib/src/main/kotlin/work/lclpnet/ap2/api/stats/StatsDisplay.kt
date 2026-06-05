package work.lclpnet.ap2.api.stats

import net.minecraft.ChatFormatting.*
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.dialog.*
import net.minecraft.server.dialog.body.DialogBody
import net.minecraft.server.dialog.body.PlainMessage
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.slf4j.Logger
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.ext.component1
import work.lclpnet.ap2.ext.component2
import work.lclpnet.ap2.impl.util.TimeHelper
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.*

class StatsDisplay(val translations: Translations, val logger: Logger) {

    private val sectionWidth = 200

    fun openSummary(player: ServerPlayer, stats: StatsResult) {
        when (stats) {
            is FFAStatsResult -> openFfaSummary(player, stats)
            is TeamStatsResult -> openTeamSummary(player, stats)
            else -> logger.warn("Stats summary not implemented for result type {} ({})", stats.type, stats.javaClass.simpleName)
        }
    }

    private fun openFfaSummary(player: ServerPlayer, stats: FFAStatsResult) {
        val view = stats.view

        if (view.results.isEmpty()) return

        val body = mutableListOf<DialogBody>()

        appendGameSummary(player, stats.summary, body)

        appendSections(body, view, stats.summary.game.id, player, ranking = true) { ref, _ ->
            val name = ref.getNameFor(player)
            if (name.style.color == null) name.copy().withStyle(GREEN) else name
        }

        showDialog(player, body)
    }

    private fun appendGameSummary(
        player: ServerPlayer,
        summary: GameSummary,
        body: MutableList<DialogBody>
    ) {
        val title = translations.translateText(player, summary.game.titleKey)
            .formatted(GOLD, BOLD)

        val mapName = summary.map.getName(translations.getLanguage(player))
        val mapLine = translations.translateText(
            player,
            "ap2.view_stats.map",
            FormatWrapper.styled(mapName, AQUA)
        ).formatted(GREEN)

        val seconds = summary.duration.inWholeSeconds.toInt()
        val durationTime = TimeHelper.formatTime(translations, seconds).formatted(YELLOW)
        val durationLine = translations.translateText(player, "ap2.view_stats.duration", durationTime)
            .formatted(GREEN)

        val text = Component.empty()
            .append(title)
            .append(Component.literal("\n")).append(mapLine)
            .append(Component.literal("\n")).append(durationLine)

        body.add(PlainMessage(text, sectionWidth))
        body.add(separator())
    }

    private fun openTeamSummary(player: ServerPlayer, stats: TeamStatsResult) {
        val teamView = stats.teamView
        val playerView = stats.playerView

        val body = mutableListOf<DialogBody>()

        appendGameSummary(player, stats.summary, body)

        val statsBody = mutableListOf<DialogBody>()

        if (teamView.results.isNotEmpty()) {
            statsBody.add(categoryHeader(translations.translateText("ap2.view_stats.teams").translateFor(player)))

            appendSections(statsBody, teamView, stats.summary.game.id, player, ranking = true) { ref, _ ->
                ref.getNameFor(player)
            }
        }

        if (playerView.results.isNotEmpty()) {
            if (statsBody.isNotEmpty()) statsBody.add(separator())

            statsBody.add(categoryHeader(translations.translateText("ap2.view_stats.players").translateFor(player)))

            appendSections(statsBody, playerView, stats.summary.game.id, player, ranking = false) { ref, _ ->
                val name = ref.getNameFor(player)
                val teamRef = stats.playerTeams[ref]

                when {
                    teamRef != null -> name.copy().withStyle { it.withColor(teamRef.key().color()) }
                    name.style.color == null -> name.copy().withStyle(GREEN)
                    else -> name
                }
            }
        }

        if (statsBody.isEmpty()) {
            val contents = translations.translateText(player, "ap2.view_stats.no_content")
            statsBody.add(PlainMessage(contents, sectionWidth))
        }

        body.addAll(statsBody)

        showDialog(player, body)
    }

    private fun <Ref : SubjectRef> appendSections(
        body: MutableList<DialogBody>,
        view: StatsView<Ref>,
        gameId: Identifier,
        player: ServerPlayer,
        ranking: Boolean,
        renderName: (ref: Ref, position: Int) -> Component,
    ) {
        val ranks = HashMap<Ref, Int>()

        for ((ref, rank) in view.order) {
            if (ref != null) ranks[ref] = rank
        }

        if (ranking) body.add(rankingSection(view, player, renderName))

        for (stat in view.stats) {
            body.add(statSection(view, stat, gameId, player, ranks, renderName))
        }
    }

    private fun <Ref : SubjectRef> rankingSection(
        view: StatsView<Ref>,
        player: ServerPlayer,
        renderName: (ref: Ref, position: Int) -> Component,
    ): PlainMessage {
        val label = translations.translate(player, "ap2.view_stats.ranking")

        val text = Component.empty()
            .append(Component.literal(label).withStyle(GOLD, BOLD))

        for ((ref, rank) in view.order) {
            if (ref == null) continue

            text.append(Component.literal("\n"))
                .append(Component.literal("#$rank ").withStyle(YELLOW))
                .append(renderName(ref, rank))
        }

        return PlainMessage(text, sectionWidth)
    }

    private fun <Ref : SubjectRef> statSection(
        view: StatsView<Ref>,
        stat: Stat<out Any>,
        gameId: Identifier,
        player: ServerPlayer,
        ranks: Map<Ref, Int>,
        renderName: (ref: Ref, position: Int) -> Component,
    ): PlainMessage {
        val ordered = view.results.entries.sortedWith(
            compareByDescending<Map.Entry<Ref, Stats>> { sortKey(it.value, stat) }
                .thenBy { ranks[it.key] ?: Int.MAX_VALUE }
        )

        val text = Component.empty()
            .append(Component.literal(labelOf(gameId, stat, player)).withStyle(GOLD, BOLD))

        ordered.forEachIndexed { index, (ref, result) ->
            val position = index + 1

            val formattedValue = formatValue(result[stat], translations.getLocale(player))

            text.append(Component.literal("\n"))
                .append(renderName(ref, position))
                .append(Component.literal("  "))
                .append(Component.literal(formattedValue).withColor(positionColor(position)))
        }

        return PlainMessage(text, sectionWidth)
    }

    private fun formatValue(value: Any, locale: Locale): String = when (value) {
        is Float, is Double -> String.format(locale, "%.2f", value)
        else -> value.toString()
    }

    private fun sortKey(result: Stats, stat: Stat<*>): Double {
        val value = (result[stat] as? Number)?.toDouble() ?: 0.0
        return if (stat.higherIsBetter) value else -value
    }

    private fun positionColor(position: Int) = when (position) {
        1 -> 0xffaa00
        2 -> 0xc0c0c0
        3 -> 0xcd7f32
        else -> 0xffffff
    }

    private fun categoryHeader(label: Component): PlainMessage =
        PlainMessage(label.copy().withStyle(AQUA, BOLD), sectionWidth)

    private fun separator(): PlainMessage {
        val line = Component.literal("=".repeat(26)).withStyle(DARK_GREEN, STRIKETHROUGH, BOLD)
        return PlainMessage(line, sectionWidth)
    }

    private fun showDialog(player: ServerPlayer, body: List<DialogBody>) {
        val title = translations.translateText("ap2.stats").formatted(GOLD).translateFor(player)

        val commonData = CommonDialogData(
            title, Optional.empty(), true, false, DialogAction.CLOSE, body, listOf()
        )

        val back = ActionButton(
            CommonButtonData(Component.translatable("gui.back"), 200),
            Optional.empty()
        )

        val dialog = NoticeDialog(commonData, back)

        player.openDialog(Holder.direct(dialog))
    }

    private fun labelOf(
        gameId: Identifier,
        stat: Stat<*>,
        player: ServerPlayer
    ): String {
        val gameKey = gameId.toLanguageKey().replace('/', '.')
        val gameStatKey = "game.$gameKey.stat.${stat.id}"

        val label = translations.translate(player, gameStatKey)

        if (label != gameStatKey) return label

        // no translation for the stat under game namespace, try root namespace instead
        val rootKey = "${gameId.namespace}.stat.${stat.id}"
        val rootLabel = translations.translate(player, rootKey)

        return when (rootKey) {
            rootLabel -> label
            else -> rootLabel  // there is a translation for the stat under the root namespace, use it instead
        }
    }

    fun unavailable(player: ServerPlayer) {
        translations.translateText("ap2.view_stats.unavailable").formatted(RED).sendTo(player)
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.5f)
    }
}
