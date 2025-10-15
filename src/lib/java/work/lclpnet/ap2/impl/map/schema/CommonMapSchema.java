package work.lclpnet.ap2.impl.map.schema;

import lombok.Getter;
import work.lclpnet.kibu.hook.util.PositionRotation;

@Getter
@MapSchema(
        namespace = "gaco",
        id = "common",
        name = "Common Map"
)
public class CommonMapSchema {

    @Property(name = "Spawn", ordinal = 0)
    @Role("spawn")
    private PositionRotation spawn = null;
}
