package work.lclpnet.ap2.game.book_collectors.setup;

import lombok.Getter;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static work.lclpnet.kibu.util.StructureWriter.Option.*;

@Getter
public class BCBaseManager {

    private final Map<Team, BCBase> bases;

    public BCBaseManager(Map<Team, BCBase> bases) {
        this.bases = bases;
    }

    public Optional<Team> blockPosInAnyBase(BlockPos pos) {
        return bases.entrySet().stream().filter(base -> base.getValue().isInside(pos.getX(), pos.getY(), pos.getZ())).map(Map.Entry::getKey).findFirst();
    }

    public void openDoors(ServerWorld world) {
        var opts = EnumSet.of(FORCE_STATE, SKIP_DROPS, SKIP_NEIGHBOUR_UPDATE);

        for (BCBase base : bases.values()) {
            BlockStructure struct = base.doorSchematic();
            BlockPos pos = base.doorPos();

            if (pos == null || struct == null) continue;

            StructureWriter.placeStructure(struct, world, pos, Matrix3i.IDENTITY, opts);
        }
    }
}
