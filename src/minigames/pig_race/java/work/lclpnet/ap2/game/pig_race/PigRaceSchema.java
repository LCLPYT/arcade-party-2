package work.lclpnet.ap2.game.pig_race;

import lombok.Getter;
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema;
import work.lclpnet.ap2.impl.map.schema.MapSchema;
import work.lclpnet.ap2.impl.map.schema.Property;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.math.SplinePath;

import java.util.List;

@MapSchema(
        namespace = "ap2",
        id = "pig_race",
        name = "Pig Race"
)
@Getter
public class PigRaceSchema extends CommonMapSchema {

    @Property(name = "Start spawn box")
    public BlockBox spawnBounds;

    @Property(name = "Start gates")
    public List<BlockBox> gates = List.of();

    @Property(name = "Goal bounds")
    public BlockBox goalBounds;

    @Property(name = "Checkpoints")
    public List<Checkpoint> checkpoints = List.of();

    @Property(name = "Path")
    public SplinePath path;
}
