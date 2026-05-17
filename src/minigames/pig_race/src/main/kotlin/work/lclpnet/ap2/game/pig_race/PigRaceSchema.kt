package work.lclpnet.ap2.game.pig_race

import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema
import work.lclpnet.ap2.impl.map.schema.MapSchema
import work.lclpnet.ap2.impl.map.schema.Property
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.Checkpoint
import work.lclpnet.gaco.math.SplinePath

@MapSchema(
    namespace = ApConstants.ID,
    id = "pig_race",
    name = "Pig Race"
)
class PigRaceSchema : CommonMapSchema() {

    @Property(name = "Start spawn box")
    val spawnBounds: BlockBox? = null

    @Property(name = "Start gates")
    val gates = listOf<BlockBox>()

    @Property(name = "Goal checkpoint")
    val goal: Checkpoint? = null

    @Property(name = "Checkpoints")
    val checkpoints = listOf<Checkpoint>()

    @Property(name = "Path")
    val path: SplinePath? = null

    @Property(name = "Progress Markers")
    val progressMarkers = listOf<Checkpoint>()
}
