package work.lclpnet.ap2.game.jump_and_run;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.impl.map.schema.CommonMapSchema;
import work.lclpnet.ap2.impl.map.schema.MapSchema;
import work.lclpnet.ap2.impl.map.schema.Property;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.ds.PositionedBlockSet;
import work.lclpnet.gaco.math.BlockFace;

import java.util.List;
import java.util.Set;

@Getter
@MapSchema(
        namespace = ApConstants.ID,
        id = "jump_and_run_module",
        name = "Jump 'n' Run module"
)
public class JumpAndRunModuleSchema extends CommonMapSchema {

    @Property(name = "Checkpoints", ordinal = 0)
    private List<Checkpoint> checkpoints = List.of();

    @Property(name = "Assistance blocks", ordinal = 1)
    private PositionedBlockSet assistance = new PositionedBlockSet(Set.of());

    @Property(name = "Custom start checkpoint", optional = true, ordinal = 2)
    @Nullable
    private final Checkpoint start = null;

    @Property(name = "Custom start gates", optional = true, ordinal = 3)
    private final List<BlockBox> startGates = List.of();

    @Property(name = "Entrance", optional = true, ordinal = 4)
    @Nullable
    private final BlockFace entrance = null;

    @Property(name = "Custom end checkpoint", optional = true, ordinal = 5)
    @Nullable
    private final Checkpoint end = null;

    @Property(name = "Exit", optional = true, ordinal = 6)
    @Nullable
    private final BlockFace exit = null;
}
