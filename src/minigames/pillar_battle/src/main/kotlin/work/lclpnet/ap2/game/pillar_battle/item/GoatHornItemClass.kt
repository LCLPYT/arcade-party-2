package work.lclpnet.ap2.game.pillar_battle.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.tags.InstrumentTags
import net.minecraft.world.item.InstrumentItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.*
import java.util.stream.Stream

class GoatHornItemClass(private val registryManager: RegistryAccess) : ItemClass {
    override fun getRandomStack(random: Random): ItemStack {
        val subRandom = net.minecraft.util.RandomSource.create(random.nextLong())

        return registryManager
            .lookupOrThrow(Registries.INSTRUMENT)
            .getRandomElementOf(InstrumentTags.GOAT_HORNS, subRandom)
            .map { InstrumentItem.create(Items.GOAT_HORN, it) }
            .orElseGet { ItemStack(Items.GOAT_HORN) }
    }

    override fun stream(): Stream<Item> = Stream.of(Items.GOAT_HORN)
}
