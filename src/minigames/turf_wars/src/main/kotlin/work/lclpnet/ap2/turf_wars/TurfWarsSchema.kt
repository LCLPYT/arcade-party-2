package work.lclpnet.ap2.turf_wars

import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.hook.util.PositionRotation

@MapSchema(
    namespace = ApConstants.ID,
    id = "turf_wars",
    name = "Turf Wars"
)
class TurfWarsSchema : CommonMapSchema() {

    @Property(name = "Team 1 Spawn")
    val team1Spawn: PositionRotation? = null

    @Property(name = "Team 1 Turf")
    val team1Turf: BlockBox? = null

    @Property(name = "Team 1 Base")
    val team1Base: BlockBox? = null

    @Property(name = "Team 2 Spawn")
    val team2Spawn: PositionRotation? = null

    @Property(name = "Team 2 Turf")
    val team2Turf: BlockBox? = null

    @Property(name = "Team 2 Base")
    val team2Base: BlockBox? = null

    @Property(name = "Spawn gates")
    val spawnGates: List<BlockBox> = listOf()
}