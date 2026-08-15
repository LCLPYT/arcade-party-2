package work.lclpnet.ap2.capture_the_flag

import net.minecraft.core.BlockPos
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.hook.util.PositionRotation

@MapSchema(
    namespace = ApConstants.ID,
    id = "capture_the_flag",
    name = "Capture the Flag"
)
class CtfSchema : CommonMapSchema() {

    @Property(name = "Team 1 Spawn")
    val team1Spawn: PositionRotation? = null

    @Property(name = "Team 2 Spawn")
    val team2Spawn: PositionRotation? = null

    @Property(name = "Team 1 Flag position")
    val team1FlagPos: BlockPos? = null

    @Property(name = "Team 2 Flag position")
    val team2FlagPos: BlockPos? = null

    @Property(name = "Team 1 Spawn Gate")
    val team1SpawnGate: BlockBox? = null

    @Property(name = "Team 2 Spawn Gate")
    val team2SpawnGate: BlockBox? = null

    @Property(name = "Play area scanner starts")
    val scannerStarts = listOf<BlockPos>()

    @Property(name = "Play area scanner bounds")
    val scannerBounds: BlockBox? = null
}