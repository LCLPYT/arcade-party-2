package work.lclpnet.ap2.game.jump_and_run;

import lombok.Getter;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.impl.map.schema.MapSchema;
import work.lclpnet.ap2.impl.map.schema.Property;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.ds.PositionedBlockSet;

import java.util.List;
import java.util.Set;

@Getter
@MapSchema(
        namespace = ApConstants.ID,
        id = "jump_and_run_module",
        name = "Jump 'n' Run module"
)
public class JumpAndRunModuleSchema {

    @Property(name = "Checkpoints")
    private List<Checkpoint> checkpoints = List.of();

    @Property(name = "Assistance blocks")
    private PositionedBlockSet assistance = new PositionedBlockSet(Set.of());

    @Property(name = "Custom start checkpoint", optional = true)
    private final Checkpoint start = null;

    @Property(name = "Custom end checkpoint", optional = true)
    private final Checkpoint end = null;
}
