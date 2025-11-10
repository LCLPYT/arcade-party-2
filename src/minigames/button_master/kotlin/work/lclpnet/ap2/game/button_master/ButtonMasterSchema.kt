package work.lclpnet.ap2.game.button_master

import net.minecraft.util.math.BlockPos
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox

@MapSchema(
    namespace = ApConstants.ID,
    id = "button_master",
    name = "Button Master",
)
class ButtonMasterSchema : CommonMapSchema() {

    @Property(name = "Scan start position")
    val scanPos: BlockPos? = null

    @Property(name = "Scanner bounds")
    val scanBox: BlockBox? = null
}