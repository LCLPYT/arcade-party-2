package work.lclpnet.ap2.game.pvp_tournament

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.ext.transform
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.serial.CenteredPositionRotation
import work.lclpnet.gaco.math.AffineIntMatrix
import work.lclpnet.kibu.hook.util.PositionRotation
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
        val mat = AffineIntMatrix.makeTranslation(origin.multiply(-1))

        return arena.data.spawns.map { it.transform(mat) }
    }
}
