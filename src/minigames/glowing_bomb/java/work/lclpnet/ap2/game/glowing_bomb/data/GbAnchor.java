package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.EntityRef;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

import java.util.UUID;

public class GbAnchor {

    private final UUID owner;
    private final Vec3d pos;
    private final EntityRef<DisplayEntity.BlockDisplayEntity> displayRef;
    private int charges = 0;

    public GbAnchor(UUID owner, Vec3d pos, DisplayEntity.BlockDisplayEntity display) {
        this.owner = owner;
        this.pos = pos;
        this.displayRef = new EntityRef<>(display);
    }

    @Nullable
    private DisplayEntity.BlockDisplayEntity display() {
        return displayRef.resolve();
    }

    public int charges() {
        return charges;
    }

    public Vec3d pos() {
        return pos;
    }

    public UUID owner() {
        return owner;
    }

    public void setCharges(int charges) {
        this.charges = Math.max(0, Math.min(4, charges));

        var display = display();

        if (display == null) return;

        DisplayEntityAccess.setBlockState(display, Blocks.RESPAWN_ANCHOR.getDefaultState().with(RespawnAnchorBlock.CHARGES, this.charges));
    }

    public void discard() {
        var display = display();

        if (display != null) {
            display.discard();
        }
    }
}
