package work.lclpnet.ap2.game.button_master

import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.math.BlockFace
import work.lclpnet.kibu.hook.util.PositionRotation

@MapSchema(
    namespace = ApConstants.ID,
    id = "button_master",
    name = "Button Master",
)
class ButtonMasterSchema : CommonMapSchema() {

    @Property(name = "Scanner bounds")
    val scanBox: BlockBox? = null

    @Property(name = "Button Master Spawn")
    val buttonMasterSpawn: PositionRotation? = null

    @Property(name = "Capsules")
    val capsules = listOf<BlockFace>()

    @Property(name = "Capsule Schematic Button")
    val capsuleButton: BlockFace? = null

    @Property(name = "Capsule Schematic Spawn")
    val capsuleSpawn: PositionRotation? = null

    @Property(name = "Start Walls")
    val startWalls = listOf<BlockBox>()
}