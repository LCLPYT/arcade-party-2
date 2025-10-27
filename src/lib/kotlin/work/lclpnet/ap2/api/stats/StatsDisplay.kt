package work.lclpnet.ap2.api.stats

import net.minecraft.dialog.AfterAction
import net.minecraft.dialog.DialogActionButtonData
import net.minecraft.dialog.DialogButtonData
import net.minecraft.dialog.DialogCommonData
import net.minecraft.dialog.body.DialogBody
import net.minecraft.dialog.type.MultiActionDialog
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting.*
import org.slf4j.Logger
import work.lclpnet.ap2.component1
import work.lclpnet.ap2.component2
import work.lclpnet.kibu.translate.Translations
import java.util.*

class StatsDisplay(val translations: Translations, val logger: Logger) {

    fun openSummary(player: ServerPlayerEntity, stats: StatsResult) {
        val body = mutableListOf<DialogBody>()

        if (stats !is FFAStatsResult) {
            logger.warn("Stats summary not implemented for result type {} ({})", stats.type(), stats.javaClass.simpleName)
            return
        }

        val schema = stats.results.entries.firstOrNull()?.value ?: return

        val buttons = mutableListOf<DialogActionButtonData>()

        val playerWidth = 100
        val statWidth = 85
        val maxStatColumns = 4
        val columns = schema.entries().size.coerceAtMost(maxStatColumns) + 1

        buttons.add(DialogActionButtonData(
            DialogButtonData(translations.translateText("ap2.view_stats.name").translateFor(player), playerWidth),
            Optional.empty()
        ))

        for ((stat, _) in schema.entries().take(maxStatColumns)) {
            buttons.add(DialogActionButtonData(
                DialogButtonData(Text.literal(labelOf(stats, stat, player)), statWidth),
                Optional.empty()
            ))
        }

        for ((ref, rank) in stats.order) {
            if (ref == null) continue

            val result = stats.results[ref] ?: continue

            val name = ref.getNameFor(player).let {
                if (it.style.color == null) it.copy().formatted(GREEN)
                else it
            }

            buttons.add(DialogActionButtonData(
                DialogButtonData(
                    Text.literal("#$rank ")
                        .formatted(YELLOW)
                        .append(name),
                    playerWidth
                ),
                Optional.empty()
            ))

            for ((_, value) in result.entries().take(maxStatColumns)) {
                buttons.add(DialogActionButtonData(
                    DialogButtonData(Text.literal(value.toString()), statWidth),
                    Optional.empty()
                ))
            }
        }

        val title = translations.translateText("ap2.stats").formatted(GOLD).translateFor(player)

        val commonData = DialogCommonData(
            title, Optional.empty(), true, false, AfterAction.NONE, body, listOf()
        )

        val dialog = MultiActionDialog(
            commonData,
            buttons,
            Optional.of(DialogActionButtonData(
                DialogButtonData(Text.translatable("gui.back"), 150),
                Optional.empty()
            )),
            columns
        )

        player.openDialog(RegistryEntry.of(dialog))
    }
    private fun labelOf(
        statsResult: FFAStatsResult,
        stat: Stat<*>,
        player: ServerPlayerEntity
    ): String {
        val gameKey = statsResult.gameId.toTranslationKey().replace('/', '.')
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

    fun unavailable(player: ServerPlayerEntity) {
        translations.translateText("ap2.view_stats.unavailable").formatted(RED).sendTo(player)
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.5f, 0.5f)
    }
}