package work.lclpnet.ap2.game.speed_builders.data;

import net.minecraft.util.math.Vec3i;
import work.lclpnet.kibu.structure.BlockStructure;

public record SbModule(String id, BlockStructure structure) {

    public boolean isCompatibleWith(Vec3i dimensions) {
        return structure.getWidth() == dimensions.getX() && structure.getHeight() <= dimensions.getY() && structure.getLength() == dimensions.getZ();
    }

    public int getMaxScore() {
        // one point per block in cuboid (excluding the floor)
        return structure.getWidth() * structure.getLength() * (structure.getHeight() - 1) + structure.getEntities().size();
    }
}
