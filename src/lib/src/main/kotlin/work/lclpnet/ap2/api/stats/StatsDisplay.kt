package work.lclpnet.ap2.api.stats

import net.minecraft.ChatFormatting.*
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.dialog.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.slf4j.Logger
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.ext.component1
import work.lclpnet.ap2.ext.component2
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.translate.Translations
import java.util.*

class StatsDisplay(val translations: Translations, val logger: Logger) {

    private val playerWidth = 100
    private val statWidth = 85
    private val maxStatColumns = 4

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

        val columns = 1 + view.stats.size.coerceAtMost(maxStatColumns)

        val buttons = mutableListOf<ActionButton>()

        appendTable(
            buttons, view, stats.gameId, player, columns,
            nameHeader = translations.translateText("ap2.view_stats.name").translateFor(player),
        ) { ref, rank ->
            val name = ref.getNameFor(player).let {
                if (it.style.color == null) it.copy().withStyle(GREEN)
                else it
            }

            Component.literal("#$rank ")
                .withStyle(YELLOW)
                .append(name)
        }

        showDialog(player, buttons, columns)
    }

    private fun openTeamSummary(player: ServerPlayer, stats: TeamStatsResult) {
        val teamView = stats.teamView
        val playerView = stats.playerView

        val teamStatColumns = teamView.stats.size.coerceAtMost(maxStatColumns)
        val playerStatColumns = playerView.stats.size.coerceAtMost(maxStatColumns)
        val columns = 1 + maxOf(teamStatColumns, playerStatColumns)

        val buttons = mutableListOf<ActionButton>()

        appendTable(
            buttons, teamView, stats.gameId, player, columns,
            nameHeader = translations.translateText("ap2.view_stats.team").translateFor(player),
        ) { ref, rank ->
            Component.literal("#$rank ")
                .withStyle(YELLOW)
                .append(ref.getNameFor(player))
        }

        pad(buttons, columns, 0)

        appendTable(
            buttons, playerView, stats.gameId, player, columns,
            nameHeader = translations.translateText("ap2.view_stats.name").translateFor(player),
        ) { ref, _ ->
            val name = ref.getNameFor(player)
            val teamRef = stats.playerTeams[ref]

            when {
                teamRef != null -> name.copy().withStyle { it.withColor(teamRef.key().color()) }
                name.style.color == null -> name.copy().withStyle(GREEN)
                else -> name
            }
        }

        showDialog(player, buttons, columns)
    }

    private fun <Ref : SubjectRef> appendTable(
        buttons: MutableList<ActionButton>,
        view: StatsView<Ref>,
        gameId: Identifier,
        player: ServerPlayer,
        totalColumns: Int,
        nameHeader: Component,
        renderName: (ref: Ref, rank: Int) -> Component,
    ) {
        val statColumns = view.stats.toList().take(maxStatColumns)

        buttons.add(actionButton(nameHeader, playerWidth))

        for (stat in statColumns) {
            buttons.add(actionButton(Component.literal(labelOf(gameId, stat, player)), statWidth))
        }

        pad(buttons, totalColumns, 1 + statColumns.size)

        for ((ref, rank) in view.order) {
            if (ref == null) continue

            val result = view.results[ref] ?: continue

            buttons.add(actionButton(renderName(ref, rank), playerWidth))

            for (stat in statColumns) {
                buttons.add(actionButton(Component.literal(result[stat].toString()), statWidth))
            }

            pad(buttons, totalColumns, 1 + statColumns.size)
        }
    }

    private fun pad(buttons: MutableList<ActionButton>, totalColumns: Int, used: Int) {
        repeat(totalColumns - used) {
            buttons.add(actionButton(Component.empty(), statWidth))
        }
    }

    private fun actionButton(label: Component, width: Int) =
        ActionButton(CommonButtonData(label, width), Optional.empty())

    private fun showDialog(player: ServerPlayer, buttons: List<ActionButton>, columns: Int) {
        val title = translations.translateText("ap2.stats").formatted(GOLD).translateFor(player)

        val commonData = CommonDialogData(
            title, Optional.empty(), true, false, DialogAction.NONE, listOf(), listOf()
        )

        val dialog = MultiActionDialog(
            commonData,
            buttons,
            Optional.of(
                ActionButton(
                    CommonButtonData(Component.translatable("gui.back"), 150),
                    Optional.empty()
                )
            ),
            columns
        )

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