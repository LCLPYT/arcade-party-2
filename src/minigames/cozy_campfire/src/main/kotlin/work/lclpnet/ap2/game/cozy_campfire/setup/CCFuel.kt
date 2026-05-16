package work.lclpnet.ap2.game.cozy_campfire.setup

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.FuelValues
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.type.ApFuelRegistry

class CCFuel(
    private val world: ServerLevel,
    private val baseManager: CCBaseManager
) {

    private val breakableBlocks = mutableSetOf<net.minecraft.world.level.block.Block>()
    private val fuel = Object2IntOpenHashMap<Item>()

    fun registerFuel(fuelPerSecond: Int) {
        val fuelRegistry: FuelValues = world.fuelValues()
        val fuelAccess = fuelRegistry as ApFuelRegistry

        for (item in fuelRegistry.fuelItems()) {
            val ticks = fuelAccess.`ap2$getFuelTicks`(item)
            if (ticks > 0) fuel.put(item, ticks)
        }

        addFuel(ItemTags.LEAVES, 50)
        addFuel(ItemTags.BEDS, 2200)
        fuel.put(Items.VINE, 40)
        fuel.put(Items.BEEHIVE, 250)
        fuel.put(Items.BOOKSHELF, 400)
        fuel.put(Items.CHISELED_BOOKSHELF, 410)
        fuel.put(Items.TARGET, 160)
        addFuel(ItemTags.WOODEN_DOORS, 350)

        fuel.put(Items.CHARCOAL, 530)
        fuel.put(Items.COAL, 530)
        fuel.put(Items.DRIED_KELP_BLOCK, fuelPerSecond * 4)

        fuel.keys
            .filterIsInstance<BlockItem>()
            .map { it.block }
            .forEach { breakableBlocks.add(it) }

        breakableBlocks.add(Blocks.FIRE)

        transform(fuelPerSecond)
    }

    private fun addFuel(tag: TagKey<Item>, time: Int) {
        for (entry in BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            if (entry.`is`(ItemTags.NON_FLAMMABLE_WOOD)) continue
            fuel.put(entry.value(), time)
        }
    }

    private fun transform(fuelPerSecond: Int) {
        val lowerBound = fuelPerSecond / 2
        val upperBound = fuelPerSecond * 5

        for (item in fuel.keys.toList()) {
            fuel.put(item, Mth.clamp(fuel.getInt(item), lowerBound, upperBound))
        }
    }

    fun isFuel(player: ServerPlayer, pos: BlockPos): Boolean {
        val x = pos.x + 0.5
        val y = pos.y + 0.5
        val z = pos.z + 0.5

        if (baseManager.isInAnyBase(x, y, z)) return false

        val state = world.getBlockState(pos)
        if (breakableBlocks.contains(state.block)) return true

        val stack = player.mainHandItem
        val blockEntity = world.getBlockEntity(pos)

        val builder = LootParams.Builder(world)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, stack)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)

        return state.getDrops(builder).any { isFuel(it) }
    }

    fun isFuel(stack: ItemStack) = fuel.containsKey(stack.item)

    fun getValue(stack: ItemStack) = fuel.getOrDefault(stack.item, 0) * stack.count
}
