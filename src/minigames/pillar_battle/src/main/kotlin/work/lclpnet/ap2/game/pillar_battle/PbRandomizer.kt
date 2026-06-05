package work.lclpnet.ap2.game.pillar_battle

import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.pillar_battle.item.*
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.tags.ApItemTags
import work.lclpnet.gaco.ds.IndexedSet
import java.util.*
import java.util.stream.Collectors

class PbRandomizer(
    private val random: Random,
    private val participants: Participants,
    private val registryManager: RegistryAccess
) {
    private val itemClasses = IndexedSet<ItemClass>()

    init {
        initItems()
    }

    private fun initItems() {
        group(ItemTags.BANNERS)
        group(ItemTags.BEDS)
        group(ItemTags.CANDLES)
        group(ItemTags.DECORATED_POT_SHERDS)
        group(ItemTags.BUNDLES)
        group(ApItemTags.TRIM_TEMPLATES)
        group(ApItemTags.FLOWERS)
        group(ApItemTags.DYES)
        group(ApItemTags.BANNER_PATTERNS)
        group(ItemTags.HARNESSES)
        group(ItemTags.SHULKER_BOXES)

        itemClasses.add(PotionItemClass(Items.POTION))
        itemClasses.add(PotionItemClass(Items.SPLASH_POTION))
        itemClasses.add(PotionItemClass(Items.LINGERING_POTION))
        itemClasses.add(PotionItemClass(Items.TIPPED_ARROW))

        itemClasses.add(EnchantedBookItemClass(registryManager))
        itemClasses.add(GoatHornItemClass(registryManager))
        itemClasses.add(SuspiciousStewItemClass())

        val exclude: Set<Item> = itemClasses.stream()
            .flatMap { it.stream() }
            .collect(Collectors.toSet())

        BuiltInRegistries.ITEM.stream()
            .filter { !exclude.contains(it) }
            .map { SingletonItemClass(it) }
            .forEach { itemClasses.add(it) }
    }

    private fun group(tag: net.minecraft.tags.TagKey<Item>) {
        val itemClass = MultiItemClass.ofTag(tag) ?: return
        itemClasses.add(itemClass)
    }

    fun giveRandomItems() {
        for (player in participants) {
            giveRandomItem(player)
        }
    }

    private fun giveRandomItem(player: ServerPlayer) {
        player.addItem(getRandomStack())
    }

    private fun getRandomStack() = itemClasses.get(random.nextInt(itemClasses.size)).getRandomStack(random)
}
