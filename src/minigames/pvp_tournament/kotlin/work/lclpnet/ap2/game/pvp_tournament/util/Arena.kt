package work.lclpnet.ap2.game.pvp_tournament.util

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import work.lclpnet.ap2.ext.mc.minus
import work.lclpnet.ap2.ext.transform
import work.lclpnet.ap2.serial.CenteredPositionRotation
import work.lclpnet.gaco.math.AffineIntMatrix
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.structure.BlockStructure

@Serializable
data class ArenaData(
    val id: String,
    val finale: Boolean = false,
    val spawns: List<CenteredPositionRotation> = listOf(),
) {
    init {
        require(spawns.size >= 2) { "Arena '$id' must have at least two spawns" }
    }
}

data class Arena(
    val data: ArenaData,
    val structure: BlockStructure,
)

data class ArenaInstance(
    val arena: Arena,
    val origin: BlockPos,
) {
    val spawns: List<PositionRotation> get() {
        val structureOrigin = FabricBlockStateAdapter.getInstance().revert(arena.structure.origin)

        val mat = AffineIntMatrix.makeTranslation(origin - structureOrigin)

        return arena.data.spawns.map { it.transform(mat) }
    }
}
