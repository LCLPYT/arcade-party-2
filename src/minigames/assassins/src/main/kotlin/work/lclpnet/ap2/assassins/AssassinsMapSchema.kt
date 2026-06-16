package work.lclpnet.ap2.assassins

import net.minecraft.core.BlockPos
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox

@MapSchema(
    namespace = ApConstants.ID,
    id = "assassins",
    name = "Assassins"
)
class AssassinsMapSchema : CommonMapSchema() {

    @Property(name = "Spawn box")
    val spawnBox: BlockBox? = null

    @Property(name = "Spawn scanner starts")
    val scanStarts: List<BlockPos> = listOf()
}
