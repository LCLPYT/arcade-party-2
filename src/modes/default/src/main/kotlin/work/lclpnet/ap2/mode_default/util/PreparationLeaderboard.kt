package work.lclpnet.ap2.mode_default.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.game.player.PlayerManager
import work.lclpnet.ap2.util.scoreboard.CustomObjective
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardEntry
import work.lclpnet.ap2.util.scoreboard.VirtualScoreboardObjective
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.*

/**
 * Per-player preparation sidebar.
 */
class PreparationLeaderboard(
    private val server: MinecraftServer,
    private val translations: Translations,
    private val scoreManager: ScoreManager,
    private val playerManager: PlayerManager
) : VirtualScoreboardObjective {

    private val objectives = HashMap<UUID, CustomObjective>()

    private val isFinale = playerManager.isFinale
    private val ranked = scoreManager.iterateRankedScores().toList()
    private val top5 = ranked.take(5)
    private val shownUuids = top5.mapTo(HashSet()) { it.first.uuid }
    private val lastTopRank = top5.lastOrNull()?.second ?: 0

    override fun add(player: ServerPlayer) {
        remove(player)

        val lines = buildLines(player)

        val title = translations.translateText(player, "game.${ApConstants.ID}.title")
            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)

        val objective = CustomObjective(
            "ap2_prep_sidebar", title, ObjectiveCriteria.RenderType.INTEGER, BlankFormat.INSTANCE
        )

        lines.forEachIndexed { i, line ->
            objective.setEntry("l$i", CustomScoreboardEntry(line.display, line.number, lines.size - i))
        }

        objectives[player.uuid] = objective

        objective.add(player)
        objective.setDisplay(player, DisplaySlot.SIDEBAR)
        objective.syncScores(player)
    }

    override fun remove(player: ServerPlayer) {
        val objective = objectives.remove(player.uuid) ?: return

        CustomObjective.setDisplay(player, null, DisplaySlot.SIDEBAR)
        objective.remove(player)
    }

    override fun update(player: ServerPlayer) = add(player)

    override fun unload() {
        for (uuid in objectives.keys) {
            val player = server.playerList.getPlayer(uuid) ?: continue

            CustomObjective.setDisplay(player, null, DisplaySlot.SIDEBAR)
            objectives[uuid]?.remove(player)
        }

        objectives.clear()
    }

    private fun buildLines(viewer: ServerPlayer): List<Line> {
        val lines = ArrayList<Line>()

        lines += text(separator(ApConstants.SCOREBOARD_SEPARATOR, bold = true))

        lines += Line(
            translations.translateText(viewer, "ap2.prepare.round").withStyle(ChatFormatting.GREEN),
            FixedFormat(Component.literal(scoreManager.round.toString()).withStyle(ChatFormatting.YELLOW))
        )

        if (isFinale) {
            lines += text(Component.empty())
            lines += text(translations.translateText(viewer, "ap2.finale")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            lines += text(separator(ApConstants.SCOREBOARD_SEPARATOR_SM, bold = false))

            for (finalist in scoreManager.finalists) {
                lines += text(Component.literal("• ${finalist.scoreboardName}").withStyle(ChatFormatting.GREEN))
            }
        } else if (ranked.isNotEmpty()) {
            lines += text(Component.empty())
            lines += text(translations.translateText(viewer, "ap2.score")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            lines += text(separator(ApConstants.SCOREBOARD_SEPARATOR_SM, bold = false))

            for ((ref, rank) in top5) {
                lines += scoreLine(rank, ref.name, scoreManager.getScore(ref))
            }

            val ownLineShown = playerManager.isParticipating(viewer) && viewer.uuid !in shownUuids
            val ownRank = scoreManager.rank(viewer)

            if (ownLineShown) {
                // "..." for better-ranked players hidden between the top scores and the own line
                if (ranked.any { it.first.uuid !in shownUuids && it.second < ownRank }) {
                    lines += placeholder()
                }

                lines += scoreLine(ownRank, viewer.scoreboardName, scoreManager.score(viewer))
            }

            // "..." for players hidden below the last shown line
            val shownForViewer = if (ownLineShown) shownUuids + viewer.uuid else shownUuids
            val lastShownRank = if (ownLineShown) ownRank else lastTopRank

            if (ranked.any { (ref, rank) -> ref.uuid !in shownForViewer && rank > lastShownRank }) {
                lines += placeholder()
            }
        }

        lines += text(Component.empty())

        if (isFinale) {
            val key = if (playerManager.isParticipating(viewer)) "ap2.prepare.win_finale" else "ap2.prepare.spectating"

            lines += text(translations.translateText(viewer, key).withStyle(ChatFormatting.AQUA))
        } else {
            val requiredScore = FormatWrapper.styled(scoreManager.targetScore).formatted(ChatFormatting.YELLOW)

            lines += text(translations.translateText(viewer, "ap2.prepare.score_required", requiredScore)
                .withStyle(ChatFormatting.AQUA))
        }

        lines += text(separator(ApConstants.SCOREBOARD_SEPARATOR, bold = true))

        return lines
    }

    private fun scoreLine(rank: Int, name: String, score: Int): Line {
        val display = Component.literal("#$rank ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(name).withStyle(ChatFormatting.GREEN))

        val number = FixedFormat(
            Component.literal(score.toString()).withStyle(ChatFormatting.YELLOW)
        )

        return Line(display, number)
    }

    private fun placeholder() =
        text(Component.literal("...").withStyle(ChatFormatting.GREEN))

    private fun text(component: Component) =
        Line(component, BlankFormat.INSTANCE)

    private fun separator(text: String, bold: Boolean): Component {
        val formattings = if (bold) {
            arrayOf(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD)
        } else
            arrayOf(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH)

        return Component.literal(text).withStyle(*formattings)
    }

    private data class Line(val display: Component, val number: NumberFormat)
}
