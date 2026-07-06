package work.lclpnet.ap2.game.util

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.game.data.DataEntry
import work.lclpnet.ap2.game.data.PlayerSubjectRefFactory
import work.lclpnet.ap2.game.data.SubjectRef
import work.lclpnet.ap2.util.FontService
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.function.Function
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class ResultAnnouncement<Ref : SubjectRef>(
    private val translations: Translations,
    private val font: FontService,
    private val refs: PlayerSubjectRefFactory<Ref?>,
    private val order: List<ObjectIntPair<Ref>>,
    entryGetter: Function<Ref, DataEntry<Ref>?>
) {
    private val placement = HashMap<Ref, Int>()
    private val entryByRef: HashMap<Ref, DataEntry<Ref>> = HashMap()

    init {
        for (rankEntry in order) {
            val ref = rankEntry.left()
            val entry = entryGetter.apply(ref) ?: continue

            placement[ref] = rankEntry.rightInt()
            entryByRef[ref] = entry
        }
    }

    @JvmOverloads
    fun sendTop(
        amount: Int,
        player: ServerPlayer,
        actionText: Component? = null,
        labelKey: String = "ap2.results",
    ) {
        val results = translations.translate(player, labelKey)

        val resultsText = Component.literal(results).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)

        val sep = Component.literal(ApConstants.SEPARATOR)
            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD)

        // match the secondary separator's pixel width to the bold primary separator, since the
        // non-bold '-' glyphs are narrower than the bold '=' glyphs of the same character count
        val sepWidth = font.width(ApConstants.SEPARATOR, true)
        val smLength = floor((sepWidth / font.advance('-'.code, false)).toDouble()).toInt()

        val sepSm = Component.literal("-".repeat(smLength))
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH)

        sendSeparatorWithText(player, resultsText)

        if (order.isEmpty()) {
            player.sendSystemMessage(
                translations.translateText(player, "ap2.no_results").withStyle(ChatFormatting.GRAY)
            )
        } else {
            sendRankList(amount, player)
            sendOwnScoreIfExists(player, sepSm)
        }

        if (actionText != null) {
            sendSeparatorWithText(player, actionText)
        } else {
            player.sendSystemMessage(sep)
        }
    }

    private fun sendOwnScoreIfExists(player: ServerPlayer, sepSm: MutableComponent) {
        val ownRef = refs.create(player)

        if (ownRef == null || !placement.containsKey(ownRef)) return

        val playerIndex: Int = placement[ownRef]!!
        val entry: DataEntry<Ref> = entryByRef[ownRef]!!

        sendOwnScore(player, entry, playerIndex, sepSm)
    }

    private fun sendRankList(amount: Int, player: ServerPlayer) {
        for (i in 0..<amount) {
            if (order.size <= i) break

            val rankEntry = order.get(i)
            val subject = rankEntry.left()

            val entry = entryByRef.getOrDefault(subject, null)
            val text = entry?.toText(translations)

            var subjectName = subject!!.getNameFor(player)

            if (subjectName.style.color == null) {
                subjectName = subjectName.copy().withStyle(ChatFormatting.GRAY)
            }

            val msg = Component.literal("#${rankEntry.rightInt()} ")
                .withStyle(ChatFormatting.YELLOW)
                .append(subjectName)

            if (text != null) {
                msg.append(" ").append(text.translateFor(player))
            }

            player.sendSystemMessage(msg)
        }
    }

    private fun sendSeparatorWithText(player: ServerPlayer, label: Component) {
        // the full separator and its '=' padding are bold; center the bracketed label by pixel width,
        // since the default Minecraft font is not monospaced (see FontService)
        val target = font.width(ApConstants.SEPARATOR, true)
        val eq = font.advance('='.code, true)
        val brackets = font.advance('['.code, true) + font.advance(']'.code, true)
        val labelWidth = font.width(label) + brackets

        val side = (target - labelWidth) / 2f

        if (side < eq) {
            player.sendSystemMessage(label)
            return
        }

        val left = (side / eq).roundToInt()
        val right = max(0, ((target - labelWidth - left * eq) / eq).roundToInt())

        val msg = Component.empty()
            .append(
                Component.literal("=".repeat(left))
                    .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD)
            )
            .append(Component.literal("[").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
            .append(label)
            .append(Component.literal("]").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
            .append(
                Component.literal("=".repeat(right))
                    .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD)
            )

        player.sendSystemMessage(msg)
    }

    private fun sendOwnScore(player: ServerPlayer, entry: DataEntry<Ref>, ranking: Int, sepSm: MutableComponent) {
        player.sendSystemMessage(sepSm)

        val extra = entry.toText(translations)

        if (extra == null) {
            player.sendSystemMessage(
                translations.translateText(
                    player, "ap2.you_placed",
                    FormatWrapper.styled("#$ranking", ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GRAY)
            )
            return
        }

        val translatedExtra = extra.translateFor(player)

        player.sendSystemMessage(
            translations.translateText(
                player, "ap2.you_placed_value",
                FormatWrapper.styled("#$ranking", ChatFormatting.YELLOW),
                translatedExtra.withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY)
        )
    }
}
