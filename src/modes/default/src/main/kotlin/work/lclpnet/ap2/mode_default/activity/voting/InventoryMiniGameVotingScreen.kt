package work.lclpnet.ap2.mode_default.activity.voting

import it.unimi.dsi.fastutil.objects.ReferenceSortedSets
import net.minecraft.ChatFormatting.*
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.TooltipDisplay
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.network.CustomClickActionCallback
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.inv.item.ItemStackUtil
import work.lclpnet.kibu.inv.prompt.OptionPrompt
import work.lclpnet.kibu.inv.type.RestrictedInventory
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val ROWS = 6
private const val HEADER_SIZE = 9
private const val PAGE_SIZE = (ROWS - 1) * 9

private const val SLOT_PREV = 0
private const val SLOT_SEARCH = 2
private const val SLOT_PAGE = 4
private const val SLOT_SORT = 6
private const val SLOT_NEXT = 8

private val SEARCH_SUBMIT_ID = Identifier.fromNamespaceAndPath(ApConstants.ID, "mini_game_vote/inv_search_submit")
private val SEARCH_CANCEL_ID = Identifier.fromNamespaceAndPath(ApConstants.ID, "mini_game_vote/inv_search_cancel")

/**
 * Paginated 6-row chest voting screen. The first row is a header bar (pagination, search, sort, page info);
 * the remaining 45 slots show the mini-games of the current page. Games with at least one vote are marked
 * with the enchantment glint as the at-a-glance indicator of what is currently voted for.
 */
class InventoryMiniGameVotingScreen(private val voting: MiniGameVoting) {

    private val translations = voting.translations
    private val views = HashMap<UUID, View>()

    private inner class View(val player: ServerPlayer) {
        val inventory = VotingInventory(voting.votingTitle(player))
        var search: String = ""
        var sort: MiniGameSort = MiniGameSort.OLDEST
        var page: Int = 0
        var pageGames: List<MiniGame> = emptyList()
    }

    private inner class VotingInventory(title: Component) : RestrictedInventory(ROWS, title), OptionPrompt.Handler {
        override fun onClick(event: PlayerInventoryHooks.ClickEvent) = handleClick(event)
    }

    fun open(player: ServerPlayer) {
        if (!voting.isAccepting()) return

        val view = views.getOrPut(player.uuid) { View(player) }
        render(view)
        player.openMenu(view.inventory)
    }

    fun refresh() {
        for (view in views.values) {
            val menu = view.player.containerMenu

            if (menu is ChestMenu && menu.container === view.inventory) {
                render(view)
            }
        }
    }

    fun registerHooks(hooks: HookRegistrar) {
        CustomClickActionCallback.HOOK.registerWith(hooks) { player, id, payload ->
            val view = views[player.uuid] ?: return@registerWith

            when (id) {
                SEARCH_SUBMIT_ID -> {
                    view.search = compound(payload).getStringOr(VotingSearchDialog.SEARCH_KEY, "")
                    view.page = 0
                    reopen(player, view)
                }
                SEARCH_CANCEL_ID -> reopen(player, view)
            }
        }

        PlayerConnectionHooks.QUIT.registerWith(hooks) { player ->
            views.remove(player.uuid)
        }
    }

    private fun reopen(player: ServerPlayer, view: View) {
        if (!voting.isAccepting()) return
        render(view)
        player.openMenu(view.inventory)
    }

    private fun render(view: View) {
        val player = view.player
        val all = viewMiniGames(voting.options(), player, translations, view.search, view.sort)
        val pageCount = max(1, ceil(all.size / PAGE_SIZE.toDouble()).toInt())

        view.page = view.page.coerceIn(0, pageCount - 1)

        paintHeader(view, all.size, pageCount)

        val start = view.page * PAGE_SIZE
        view.pageGames = all.subList(start, min(all.size, start + PAGE_SIZE)).toList()

        for (i in 0 until PAGE_SIZE) {
            val slot = HEADER_SIZE + i
            if (i < view.pageGames.size) {
                view.inventory.setItem(slot, optionIcon(player, view.pageGames[i]))
            } else {
                view.inventory.setItem(slot, ItemStack.EMPTY)
            }
        }
    }

    private fun paintHeader(view: View, total: Int, pageCount: Int) {
        val inv = view.inventory
        val player = view.player

        for (slot in 0 until HEADER_SIZE) inv.setItem(slot, filler())

        if (view.page > 0) inv.setItem(SLOT_PREV, prevPageItem(player))
        if (view.page < pageCount - 1) inv.setItem(SLOT_NEXT, nextPageItem(player))

        inv.setItem(SLOT_SEARCH, searchItem(player, view))
        inv.setItem(SLOT_PAGE, pageItem(player, view, total, pageCount))
        inv.setItem(SLOT_SORT, sortItem(player, view))
    }

    private fun optionIcon(player: ServerPlayer, game: MiniGame): ItemStack {
        val icon = voting.baseIcon(player, game)
        val votes = voting.voteCount(game)
        val yourVote = game == voting.currentVote(player.uuid)

        if (votes > 0) {
            icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            icon.count = min(icon.maxStackSize, votes)
        }

        val lore = ArrayList(icon.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines())
        if (lore.isNotEmpty()) lore.add(Component.empty())

        lore.add(label(player, "ap2.voting.votes", styled(votes, YELLOW)).withStyle { it.withItalic(false).applyFormat(GREEN) })

        if (yourVote) {
            lore.add(label(player, "ap2.voting.your_vote").withStyle { it.withItalic(false).applyFormat(AQUA) })
        }

        ItemStackUtil.setLore(icon, lore)
        return icon
    }

    private fun handleClick(event: PlayerInventoryHooks.ClickEvent) {
        val player = event.player
        val view = views[player.uuid] ?: return
        val slot = event.handlerSlot() ?: return
        val index = slot.containerSlot

        if (index < 0 || index >= ROWS * 9) return

        if (index < HEADER_SIZE) {
            when (index) {
                SLOT_PREV -> if (view.page > 0) { view.page--; render(view) }
                SLOT_NEXT -> { view.page++; render(view) }
                SLOT_SEARCH -> VotingSearchDialog.open(player, translations, view.search, SEARCH_SUBMIT_ID, SEARCH_CANCEL_ID)
                SLOT_SORT -> {
                    view.sort = if (event.button == 1) view.sort.previous() else view.sort.next()
                    view.page = 0
                    render(view)
                }
            }
            return
        }

        val game = view.pageGames.getOrNull(index - HEADER_SIZE) ?: return
        voting.vote(player, game)
    }

    private fun filler(): ItemStack = ItemStack(Items.STAINED_GLASS_PANE.black).apply {
        set(DataComponents.ITEM_NAME, Component.empty())
        set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay(true, ReferenceSortedSets.emptySet()))
    }

    private fun prevPageItem(player: ServerPlayer): ItemStack {
        val stack = ItemStack(Items.REDSTONE_BLOCK)
        stack.set(DataComponents.ITEM_NAME, label(player, "ap2.voting.prev").withStyle(YELLOW))
        return stack
    }

    private fun nextPageItem(player: ServerPlayer): ItemStack {
        val stack = ItemStack(Items.EMERALD_BLOCK)
        stack.set(DataComponents.ITEM_NAME, label(player, "ap2.voting.next").withStyle(YELLOW))
        return stack
    }

    private fun searchItem(player: ServerPlayer, view: View): ItemStack {
        val stack = ItemStack(Items.WRITABLE_BOOK)
        stack.set(DataComponents.ITEM_NAME, label(player, "ap2.voting.search").withStyle(YELLOW))

        val line = if (view.search.isNotBlank()) {
            Component.literal("\"${view.search}\"").withStyle { it.withItalic(false).applyFormat(AQUA) }
        } else {
            label(player, "ap2.voting.search_hint").withStyle { it.withItalic(false).applyFormat(GRAY) }
        }

        ItemStackUtil.setLore(stack, listOf(line))

        return stack
    }

    private fun pageItem(player: ServerPlayer, view: View, total: Int, pageCount: Int): ItemStack {
        val stack = ItemStack(Items.PAPER)

        stack.set(DataComponents.ITEM_NAME, label(player, "ap2.voting.page", view.page + 1, pageCount).withStyle { it.withItalic(false).applyFormat(YELLOW) })

        ItemStackUtil.setLore(stack, listOf(label(player, "ap2.voting.total", total).withStyle { it.withItalic(false).applyFormat(GRAY) }))

        return stack
    }

    private fun sortItem(player: ServerPlayer, view: View): ItemStack {
        val stack = ItemStack(Items.HOPPER)
        val sortName = translations.translate(player, view.sort.labelKey)

        stack.set(DataComponents.ITEM_NAME, label(player, "ap2.voting.sort", sortName).withStyle { it.withItalic(false).applyFormat(YELLOW) })

        ItemStackUtil.setLore(stack, listOf(label(player, "ap2.voting.sort_hint").withStyle { it.withItalic(false).applyFormat(GRAY) }))

        return stack
    }

    private fun label(player: ServerPlayer, key: String, vararg args: Any): MutableComponent =
        Component.empty().append(translations.translateText(player, key, *args))

    private fun compound(payload: Optional<Tag>): CompoundTag {
        val tag = payload.orElse(null)

        return tag as? CompoundTag ?: CompoundTag()
    }
}
