package work.lclpnet.ap2.mode_default.activity.voting

import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.dialog.*
import net.minecraft.server.dialog.action.CustomAll
import net.minecraft.server.dialog.body.DialogBody
import net.minecraft.server.dialog.body.PlainMessage
import net.minecraft.server.dialog.input.TextInput
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.kibu.translate.Translations
import java.util.*

/**
 * A small, separate dialog that captures the search query. Kept apart from the main voting screen so that
 * live vote updates can never re-open over it and interrupt the player while typing.
 */
object VotingSearchDialog {

    const val SEARCH_KEY = "search"

    fun open(
        player: ServerPlayer,
        translations: Translations,
        currentSearch: String,
        submitId: Identifier,
        cancelId: Identifier
    ) {
        val label = translations.translateText("ap2.voting.search").translateFor(player)
        val hint = translations.translateText("ap2.voting.search_hint").translateFor(player)

        val inputs = listOf(
            Input(SEARCH_KEY, TextInput(200, label, true, currentSearch, 128, Optional.empty()))
        )

        val body = listOf<DialogBody>(PlainMessage(hint, 200))

        val common = CommonDialogData(
            translations.translateText("ap2.voting.search").translateFor(player),
            Optional.empty(), true, false, DialogAction.NONE, body, inputs
        )

        val submit = ActionButton(
            CommonButtonData(label, 200),
            Optional.of(CustomAll(submitId, Optional.empty()))
        )

        val cancel = ActionButton(
            CommonButtonData(Component.translatable("gui.cancel"), 200),
            Optional.of(CustomAll(cancelId, Optional.empty()))
        )

        val dialog = MultiActionDialog(common, listOf(submit), Optional.of(cancel), 1)

        player.openDialog(Holder.direct(dialog))
    }
}
