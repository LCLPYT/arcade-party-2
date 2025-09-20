package work.lclpnet.ap2.api.stats

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import net.minecraft.dialog.AfterAction
import net.minecraft.dialog.DialogActionButtonData
import net.minecraft.dialog.DialogButtonData
import net.minecraft.dialog.DialogCommonData
import net.minecraft.dialog.body.DialogBody
import net.minecraft.dialog.body.ItemDialogBody
import net.minecraft.dialog.body.PlainMessageDialogBody
import net.minecraft.dialog.type.NoticeDialog
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.ClickEvent
import net.minecraft.text.Text
import net.minecraft.util.Formatting.*
import org.slf4j.Logger
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.component1
import work.lclpnet.ap2.component2
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.kibu.translate.Translations
import java.util.*

class StatsDisplay(val translations: Translations, val logger: Logger) {

    fun openSummary(player: ServerPlayerEntity, stats: StatsResult, id: UUID) {
        val body = mutableListOf<DialogBody>()

        if (stats is FFAStatsResult) {
            listSubjects(player, stats.order, id, body)
        } else {
            logger.warn("Stats summary not implemented for result type {} ({})", stats.type(), stats.javaClass.simpleName)
            return
        }

        val title = translations.translateText("ap2.stats").formatted(GOLD).translateFor(player)
        val dialog = createNoticeDialog(title, body)

        player.openDialog(RegistryEntry.of(dialog))
    }

    private fun listSubjects(
        player: ServerPlayerEntity,
        list: List<ObjectIntPair<out SubjectRef>>,
        id: UUID,
        body: MutableList<DialogBody>
    ) {
        for ((ref, rank) in list) {
            if (ref == null) continue

            val name = ref.getNameFor(player).let {
                if (it.style.color == null) it.copy().formatted(GREEN)
                else it
            }

            val icon = ref.getIconStackFor(player.world.registryManager, player)

            val payload = NbtCompound()
            payload.putString("id", id.toString())
            payload.putString("detail", ref.identifier)

            val text = Text.literal("#$rank ")
                .formatted(YELLOW)
                .append(name)
                .styled {
                    it.withClickEvent(ClickEvent.Custom(SessionStatsRecorder.SHOW_SUMMARY, Optional.of(payload)))
                }

            body.add(ItemDialogBody(
                icon,
                Optional.of(PlainMessageDialogBody(text, 200)),
                true, false, 24, 16
            ))
        }
    }

    fun openDetail(player: ServerPlayerEntity, stats: StatsResult, detail: String) {
        if (stats is FFAStatsResult) {
            showFFAStats(player, stats, detail)
        } else {
            logger.warn("Stats details not implemented for result type {} ({})", stats.type(), stats.javaClass.simpleName)
        }
    }

    private fun showFFAStats(player: ServerPlayerEntity, statsResult: FFAStatsResult, detail: String) {
        val uuid: UUID

        try {
            uuid = UUID.fromString(detail)
        } catch (_: Throwable) {
            logger.error("Player {} requested invalid stats detail {}", player.nameForScoreboard, detail)
            unavailable(player)
            return
        }

        val stats = statsResult.results[PlayerRef.createForUuid(uuid)]

        if (stats == null) {
            unavailable(player)
            return
        }

        showFFAStats(player, stats, statsResult)
    }

    private fun showFFAStats(player: ServerPlayerEntity, stats: Stats, statsResult: FFAStatsResult) {
        val body = mutableListOf<DialogBody>()

        val entries = stats.entries().map { (stat, value) ->
            val gameKey = statsResult.gameId.toTranslationKey().replace('/', '.')
            val gameStatKey = "game.$gameKey.stat.${stat.id}"

            var label = translations.translate(player, gameStatKey)

            if (label == gameStatKey) {
                // no translation for the stat under game namespace, try root namespace instead
                val rootKey = "${statsResult.gameId.namespace}.stat.${stat.id}"
                val rootLabel = translations.translate(player, rootKey)

                if (rootKey != rootLabel) {
                    // there is a translation for the stat under the root namespace, use it instead
                    label = rootLabel
                }
            }

            label to value
        }.sortedBy { it.first }

        for ((label, value) in entries) {
            val value: String = when (value) {
                is Int -> "$value"
                else -> continue
            }

            body.add(PlainMessageDialogBody(
                Text.literal(label)
                    .append(": ")
                    .formatted(GREEN)
                    .append(Text.literal(value)
                        .formatted(YELLOW)),
                400
            ))
        }

        val title = translations.translateText("ap2.stats").formatted(GOLD).translateFor(player)
        val dialog = createNoticeDialog(title, body)

        player.openDialog(RegistryEntry.of(dialog))
    }

    fun unavailable(player: ServerPlayerEntity) {
        translations.translateText("ap2.view_stats.unavailable").formatted(RED).sendTo(player)
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.5f, 0.5f)
    }

    private fun createNoticeDialog(title: Text, body: List<DialogBody>): NoticeDialog {
        return NoticeDialog(
            DialogCommonData(
                title, Optional.empty(), true, true, AfterAction.CLOSE, body, listOf()
            ),
            DialogActionButtonData(
                DialogButtonData(Text.translatable("gui.back"), 150),
                Optional.empty()
            )
        )
    }
}