package work.lclpnet.ap2.game.pillar_battle.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.ItemEnchantments
import work.lclpnet.ap2.impl.util.ItemHelper
import java.util.*
import java.util.stream.Stream

class EnchantedBookItemClass(private val registryManager: RegistryAccess) : ItemClass {
    override fun getRandomStack(random: Random): ItemStack {
        val stack = ItemStack(Items.ENCHANTED_BOOK)

        val registry = registryManager.lookupOrThrow(Registries.ENCHANTMENT)
        val entry = ItemHelper.getRandomEntry(registry, random) ?: return stack

        val enchantment = entry.value()
        val minLevel = enchantment.minLevel
        val level = minLevel + random.nextInt(enchantment.maxLevel - minLevel + 1)

        val builder = ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack))
        builder.set(entry, level)

        stack.set(DataComponents.STORED_ENCHANTMENTS, builder.toImmutable())
        return stack
    }

    override fun stream(): Stream<Item> = Stream.of(Items.ENCHANTED_BOOK)
}
