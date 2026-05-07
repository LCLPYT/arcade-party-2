package work.lclpnet.ap2.game.quick_sg

import net.minecraft.core.BlockPos
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox

@MapSchema(
    namespace = ApConstants.ID,
    id = "quick_sg",
    name = "Quick SG"
)
class QuickSgSchema : CommonMapSchema() {

    @Property(name = "Spawn scanner bounds")
    val scanBox: BlockBox? = null

    @Property(name = "Spawn scanner start")
    val scanStart: BlockPos? = null
}