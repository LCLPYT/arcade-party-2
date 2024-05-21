package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

import java.util.UUID;

public class GbAnchor {

    private final Vec3d pos;
    private final UUID displayUuid;
    private int charges = 0;

    public GbAnchor(Vec3d pos, DisplayEntity.BlockDisplayEntity display) {
        this.pos = pos;
        this.displayUuid = display.getUuid();
    }

    @Nullable
    private DisplayEntity.BlockDisplayEntity display(ServerWorld world) {
        Entity entity = world.getEntity(displayUuid);

        if (entity instanceof DisplayEntity.BlockDisplayEntity display) {
            return display;
        }

        return null;
    }

    public void charges(int charges, ServerWorld world) {
        this.charges = Math.max(0, Math.min(4, charges));

        var display = display(world);

        if (display == null) return;

        DisplayEntityAccess.setBlockState(display, Blocks.RESPAWN_ANCHOR.getDefaultState().with(RespawnAnchorBlock.CHARGES, this.charges));
    }

    public int charges() {
        return charges;
    }

    public Vec3d pos() {
        return pos;
    }
}
