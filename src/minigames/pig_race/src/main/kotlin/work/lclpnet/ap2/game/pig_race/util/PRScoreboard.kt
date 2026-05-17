package work.lclpnet.ap2.game.pig_race.util

import net.minecraft.ChatFormatting.*
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.util.ScoreboardUtil
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreHandle
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreboardObjective
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout
import work.lclpnet.kibu.translate.text.FormatWrapper.styled

class PRScoreboard(
    private val gameHandle: MiniGameHandle,
    private val progress: PRProgress,
    private val bossBar: DynamicTranslatedPlayerBossBar
) {

    private val prevHolders = HashSet<String>()
    private val holderRemoval = HashSet<String>()

    private lateinit var objective: DynamicScoreboardObjective
    private var roundHandle: DynamicScoreHandle? = null

    fun setup() {
        val scoreboardManager = gameHandle.scoreboardManager

        objective = ScoreboardUtil.setupDynamicSidebar(scoreboardManager, gameHandle.gameInfo.titleKey)

        if (progress.rounds <= 1) return

        val text = gameHandle.translations.translateText("game.ap2.pig_race.round").formatted(GREEN)
        roundHandle = objective.createDynamicText(text, ScoreboardLayout.TOP)

        objective.createNewline(ScoreboardLayout.TOP)
    }

    fun addScoreboardRanking() {
        objective.createText(gameHandle.translations.translateText("ap2.ranking").formatted(YELLOW, BOLD))

        val separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR_SM).withStyle(DARK_GREEN, STRIKETHROUGH)
        objective.createText(separator)

        for (player in gameHandle.participants) {
            updateRoundDisplay(player)
            objective.add(player)
        }
    }

    fun updateRanking() {
        holderRemoval.addAll(prevHolders)

        val ranking = progress.getRanking()

        for (i in ranking.indices) {
            val player = ranking[i]
            val holder = player.scoreboardName

            prevHolders.add(holder)
            holderRemoval.remove(holder)

            objective.setScore(holder, ranking.size - i)
            objective.setNumberFormat(holder, BlankFormat.INSTANCE)
            objective.setDisplayName(
                holder,
                Component.literal("#${i + 1} ").withStyle(YELLOW)
                    .append(Component.literal(holder).withStyle(GREEN))
            )
        }

        for (holder in holderRemoval) {
            objective.removeEntry(holder)
            prevHolders.remove(holder)
        }

        holderRemoval.clear()
    }

    fun updateRoundDisplay(player: ServerPlayer) {
        val rounds = progress.rounds

        if (rounds <= 1) return

        val round = progress.getRound(player)
        val handle = roundHandle

        handle?.setNumberFormat(player, FixedFormat(Component.literal("$round/$rounds").withStyle(YELLOW)))

        bossBar.setArgument(player, 0, styled(round, YELLOW))
    }
}
