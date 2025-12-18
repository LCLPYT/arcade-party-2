package work.lclpnet.ap2.api.stats

import net.minecraft.ChatFormatting.*
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.server.dialog.*
import net.minecraft.server.dialog.body.DialogBody
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.slf4j.Logger
import work.lclpnet.ap2.component1
import work.lclpnet.ap2.component2
import work.lclpnet.kibu.translate.Translations
import java.util.*

class StatsDisplay(val translations: Translations, val logger: Logger) {

    fun openSummary(player: ServerPlayer, stats: StatsResult) {
        val body = mutableListOf<DialogBody>()

        if (stats !is FFAStatsResult) {
            logger.warn("Stats summary not implemented for result type {} ({})", stats.type(), stats.javaClass.simpleName)
            return
        }

        val schema = stats.results.entries.firstOrNull()?.value ?: return

        val buttons = mutableListOf<ActionButton>()

        val playerWidth = 100
        val statWidth = 85
        val maxStatColumns = 4
        val columns = schema.entries().size.coerceAtMost(maxStatColumns) + 1

        buttons.add(
            ActionButton(
            CommonButtonData(translations.translateText("ap2.view_stats.name").translateFor(player), playerWidth),
            Optional.empty()
        ))

        for ((stat, _) in schema.entries().take(maxStatColumns)) {
            buttons.add(
                ActionButton(
                CommonButtonData(Component.literal(labelOf(stats, stat, player)), statWidth),
                Optional.empty()
            ))
        }

        for ((ref, rank) in stats.order) {
            if (ref == null) continue

            val result = stats.results[ref] ?: continue

            val name = ref.getNameFor(player).let {
                if (it.style.color == null) it.copy().withStyle(GREEN)
                else it
            }

            buttons.add(
                ActionButton(
                CommonButtonData(
                    Component.literal("#$rank ")
                        .withStyle(YELLOW)
                        .append(name),
                    playerWidth
                ),
                Optional.empty()
            ))

            for ((_, value) in result.entries().take(maxStatColumns)) {
                buttons.add(
                    ActionButton(
                    CommonButtonData(Component.literal(value.toString()), statWidth),
                    Optional.empty()
                ))
            }
        }

        val title = translations.translateText("ap2.stats").formatted(GOLD).translateFor(player)

        val commonData = CommonDialogData(
            title, Optional.empty(), true, false, DialogAction.NONE, body, listOf()
        )

        val dialog = MultiActionDialog(
            commonData,
            buttons,
            Optional.of(
                ActionButton(
                CommonButtonData(Component.translatable("gui.back"), 150),
                Optional.empty()
            )),
            columns
        )

        player.openDialog(Holder.direct(dialog))
    }
    private fun labelOf(
        statsResult: FFAStatsResult,
        stat: Stat<*>,
        player: ServerPlayer
    ): String {
        val gameKey = statsResult.gameId.toLanguageKey().replace('/', '.')
        val gameStatKey = "game.$gameKey.stat.${stat.id}"

        val label = translations.translate(player, gameStatKey)

        if (label != gameStatKey) return label

        // no translation for the stat under game namespace, try root namespace instead
        val rootKey = "${statsResult.gameId.namespace}.stat.${stat.id}"
        val rootLabel = translations.translate(player, rootKey)

        return when (rootKey) {
            rootLabel -> label
            else -> rootLabel  // there is a translation for the stat under the root namespace, use it instead
        }
    }

    fun unavailable(player: ServerPlayer) {
        translations.translateText("ap2.view_stats.unavailable").formatted(RED).sendTo(player)
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.5f)
    }
}