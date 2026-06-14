package work.lclpnet.ap2.game.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.api.game.MiniGameResults
import work.lclpnet.ap2.api.game.MiniGameResults.PlayerResult
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.GenericGameResult
import work.lclpnet.ap2.api.game.data.PlayerSubjectRefFactory
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.stats.SessionStatsRecorder
import work.lclpnet.ap2.api.util.action.Action
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.type.PlayerRef.Companion.create
import work.lclpnet.ap2.game.data.type.TeamRef
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.HookFactory
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.title.Title
import work.lclpnet.kibu.translate.text.RootText
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.stream.Collectors

private const val POST_GAME_SECONDS: Int = 7

class WinSequence<T, Ref : SubjectRef>(
    private val gameHandle: MiniGameHandle,
    private val data: DataContainer<T, Ref>,
    private val refs: PlayerSubjectRefFactory<Ref>,
    private val winners: GenericGameResult<Ref>,
    private val status: MiniGameResults.Status,
    private val statsId: CompletableFuture<Optional<UUID>>
) {

    fun start(): Action<Runnable> {
        val translations = gameHandle.translations
        val server = gameHandle.server

        for (player in PlayerLookup.all(server)) {
            val msg = translations.translateText(player, "ap2.game.winner_is")

            Title.get(player).title(Component.empty(), msg.formatted(ChatFormatting.DARK_GREEN), 5, 100, 5)

            player.sendSystemMessage(msg.formatted(ChatFormatting.GRAY))
        }

        val hook = HookFactory.createArrayBacked(Runnable::class.java) { actions ->
            Runnable {
                for (action in actions) {
                    action.run()
                }
            }
        }

        var t = 0
        var i = 0

        gameHandle.rootScheduler.interval(1) { info ->
            if (t++ == 0) {
                i++
                SoundHelper.playSound(server, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.RECORDS, 0.7f, 2f)
            }

            if (i < 5) {
                if (t > 15) {
                    t = 0
                }
            }
            if (i > 4) {
                if (t >= 5) {
                    t = 0
                }
                if (i >= 8) {
                    info.cancel()
                    announceGameOver()
                }
            }
        }.whenComplete { hook.invoker().run() }

        return Action.create(hook)
    }

    private fun announceGameOver() {
        announceWinners()
        broadcastResults()

        gameHandle.rootScheduler.timeout(Ticks.seconds(POST_GAME_SECONDS)) { ->
            val resultsMap = winners.playerResults.stream().collect(
                Collectors.toMap(
                    Function { it.left() },
                    Function { PlayerResult(it.left(), it.rightInt()) }
                ))

            gameHandle.complete(MiniGameResults(status, resultsMap))
        }
    }

    private fun announceWinners() {
        val subjects = winners.winningSubjects

        if (subjects.size > 1) {
            announceMultipleWinners(winners)
            return
        }

        if (subjects.isEmpty()) {
            announceDraw()
            return
        }

        val winner = winners.winningSubjects.iterator().next()

        announceWinner(winner)
    }

    private fun announceWinner(winner: Ref) {
        val translations = gameHandle.translations
        val won = translations.translateText("ap2.won").formatted(ChatFormatting.DARK_GREEN)

        for (player in PlayerLookup.all(gameHandle.server)) {
            val ref = refs.create(player)

            var winnerName = winner.getNameFor(player)

            if (winner == ref) {
                playWinSound(player)

                if (winner is TeamRef) {
                    winnerName = translations.translateText("ap2.your_team").translateFor(player)
                }
            } else {
                playLoseSound(player)
            }

            if (winnerName.getStyle().getColor() == null) {
                winnerName = winnerName.copy().withStyle(ChatFormatting.AQUA)
            }

            Title.get(player).title(winnerName, won.translateFor(player), 5, 100, 5)
        }
    }

    private fun announceDraw() {
        val translations = gameHandle.translations
        val won = translations.translateText("ap2.won").formatted(ChatFormatting.DARK_GREEN)

        val nobody = translations.translateText("ap2.nobody").formatted(ChatFormatting.AQUA)

        for (player in PlayerLookup.all(gameHandle.server)) {
            playLoseSound(player)
            Title.get(player).title(nobody.translateFor(player), won.translateFor(player), 5, 100, 5)
        }
    }

    private fun announceMultipleWinners(winners: GenericGameResult<Ref>) {
        val translations = gameHandle.translations

        val teams = winners.winningSubjects.iterator().next() is TeamRef

        val gameOver = translations.translateText("ap2.game_over").formatted(ChatFormatting.AQUA)
        val youWon = translations.translateText(if (teams) "ap2.your_team_won" else "ap2.you_won").formatted(
            ChatFormatting.DARK_GREEN
        )
        val youLost = translations.translateText(if (teams) "ap2.your_team_lost" else "ap2.you_lost").formatted(
            ChatFormatting.DARK_RED
        )

        val winningPlayersRefs = winners.winningPlayers

        for (player in PlayerLookup.all(gameHandle.server)) {
            val subtitle: TranslatedText

            val ref = create(player)

            if (winningPlayersRefs.contains(ref)) {
                subtitle = youWon
                playWinSound(player)
            } else {
                subtitle = youLost
                playLoseSound(player)
            }

            Title.get(player).title(gameOver.translateFor(player), subtitle.translateFor(player), 5, 100, 5)
        }
    }

    private fun broadcastResults() {
        val order = winners.subjectResults

        val announcement = ResultAnnouncement(
            gameHandle.translations,
            gameHandle.fontService,
            refs,
            order
        ) { ref ->
            data.getEntry(ref)
        }

        val statsId = this.statsId.getNow(Optional.empty()).orElse(null)

        for (player in PlayerLookup.all(gameHandle.server)) {
            if (statsId == null) {
                announcement.sendTop(5, player)
                continue
            }

            val statsMsg = getStatsMessage(player, statsId)

            announcement.sendTop(5, player, statsMsg)
        }
    }

    private fun getStatsMessage(player: ServerPlayer, statsId: UUID): RootText {
        val nbt = CompoundTag()
        nbt.putString("id", statsId.toString())

        val translations = gameHandle.translations

        return translations.translateText(player, "ap2.view_stats")
            .append(" ↗")
            .styled { style -> style
                .applyFormat(ChatFormatting.AQUA)
                .withHoverEvent(HoverEvent.ShowText(translations.translateText(player, "ap2.view_stats.click")))
                .withClickEvent(ClickEvent.Custom(SessionStatsRecorder.SHOW_SUMMARY, Optional.of(nbt)))
            }
    }

    companion object {
        fun playWinSound(player: ServerPlayer) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0f)
        }

        fun playLoseSound(player: ServerPlayer) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BLAZE_DEATH, SoundSource.PLAYERS, 1f, 1f)
        }
    }
}
