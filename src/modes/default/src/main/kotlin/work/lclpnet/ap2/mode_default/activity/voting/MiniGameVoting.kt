package work.lclpnet.ap2.mode_default.activity.voting

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.game.api.option.OptionVoting
import work.lclpnet.game.api.option.VoteResult
import work.lclpnet.game.impl.Voting
import work.lclpnet.kibu.translate.Translations
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A [Voting] over mini-games that delegates its presentation to [InventoryMiniGameVotingScreen].
 *
 * The vote storage, sound, chat feedback, item handling and reminder logic of the parent class are reused;
 * only [open] is redirected to the screen, and [vote] / [removeVote] additionally refresh the open views.
 */
class MiniGameVoting(
    id: String,
    data: OptionVoting<MiniGame>,
    val translations: Translations,
) : Voting<MiniGame>(id, data, translations, false) {

    val screen = InventoryMiniGameVotingScreen(this)
    private val currentVotes = ConcurrentHashMap<UUID, MiniGame>()

    @Volatile
    private var accepting = true

    override fun open(player: ServerPlayer) {
        if (!accepting) return
        screen.open(player)
    }

    override fun vote(player: ServerPlayer, option: MiniGame) {
        super.vote(player, option)
        currentVotes[player.uuid] = option
        screen.refresh()
    }

    override fun removeVote(player: ServerPlayer) {
        super.removeVote(player)
        currentVotes.remove(player.uuid)
        screen.refresh()
    }

    override fun end(): VoteResult<MiniGame> {
        accepting = false
        return super.end()
    }

    fun isAccepting(): Boolean = accepting

    fun options(): Collection<MiniGame> = data.options()

    fun voteCount(game: MiniGame): Int = currentResult.votes(game)

    fun currentVote(uuid: UUID): MiniGame? = currentVotes[uuid]

    fun baseIcon(player: ServerPlayer, game: MiniGame): ItemStack = data.optionIcons().apply(player, game)

    fun votingTitle(player: ServerPlayer): Component = data.title().apply(player)
}
