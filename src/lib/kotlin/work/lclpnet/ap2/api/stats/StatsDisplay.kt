package work.lclpnet.ap2.api.stats

import net.minecraft.dialog.AfterAction
import net.minecraft.dialog.DialogActionButtonData
import net.minecraft.dialog.DialogButtonData
import net.minecraft.dialog.DialogCommonData
import net.minecraft.dialog.body.DialogBody
import net.minecraft.dialog.body.ItemDialogBody
import net.minecraft.dialog.body.PlainMessageDialogBody
import net.minecraft.dialog.type.NoticeDialog
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting.GOLD
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.kibu.translate.Translations
import java.util.*

class StatsDisplay<T, Ref : SubjectRef>(val translations: Translations, val order: List<Ref>, val statsManager: StatsManager<T, Ref>) {

    fun open(player: ServerPlayerEntity) {
        val title = translations.translateText("ap2.stats").formatted(GOLD).translateFor(player)

        val body = mutableListOf<DialogBody>();

        for (ref in order) {
            val name = ref.getNameFor(player)
            val icon = ref.getIconStackFor(player.world.registryManager, player)

            body.add(ItemDialogBody(
                icon,
                Optional.of(PlainMessageDialogBody(name, 200)),
                true, true, 16, 16
            ))
        }

        val dialog = NoticeDialog(
            DialogCommonData(
                title, Optional.empty(), true, true, AfterAction.CLOSE, body, listOf()
            ),
            DialogActionButtonData(
                DialogButtonData(Text.literal("Close"), 150),
                Optional.empty()
            )
        )

        player.openDialog(RegistryEntry.of(dialog));
    }
}