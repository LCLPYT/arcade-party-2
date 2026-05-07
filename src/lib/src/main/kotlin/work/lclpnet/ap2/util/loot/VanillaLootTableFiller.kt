package work.lclpnet.ap2.util.loot

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

class VanillaLootTableFiller(val key: ResourceKey<LootTable>) : LootFiller {

    override fun fill(
        pos: BlockPos,
        level: ServerLevel,
        container: Container
    ) {
        val lootTable = level.server.reloadableRegistries().getLootTable(key)

        val builder = LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))

        lootTable.fill(container, builder.create(LootContextParamSets.CHEST), Random.nextLong())
    }
}