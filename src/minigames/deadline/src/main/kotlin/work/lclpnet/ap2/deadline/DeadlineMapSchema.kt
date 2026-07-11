package work.lclpnet.ap2.deadline

import net.minecraft.core.BlockPos
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox

@MapSchema(
    namespace = ApConstants.ID,
    id = "deadline",
    name = "Deadline"
)
class DeadlineMapSchema : CommonMapSchema() {

    @Property(name = "Power-up spawns")
    val powerUpSpawns: List<BlockPos> = listOf()

    @Property(name = "Spawn box")
    val spawnBox: BlockBox? = null

    @Property(name = "Spawn scanner starts")
    val scanStarts: List<BlockPos> = listOf()
}
