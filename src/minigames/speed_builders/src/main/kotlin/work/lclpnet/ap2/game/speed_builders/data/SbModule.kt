package work.lclpnet.ap2.game.speed_builders.data

import net.minecraft.core.Vec3i
import work.lclpnet.kibu.structure.BlockStructure

data class SbModule(val id: String, val structure: BlockStructure) {

    fun isCompatibleWith(dimensions: Vec3i): Boolean =
        structure.width == dimensions.x && structure.height <= dimensions.y && structure.length == dimensions.z

    fun getMaxScore(): Int =
        structure.width * structure.length * (structure.height - 1) + structure.entities.size

    fun getComplexity(): Int {
        val groundBlockCount = structure.width * structure.length
        return structure.blockCount + structure.entities.size - groundBlockCount
    }
}
