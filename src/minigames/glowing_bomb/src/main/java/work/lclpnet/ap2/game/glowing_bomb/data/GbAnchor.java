package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.gaco.core.api.EntityRef;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;

import java.util.UUID;

import static java.lang.Math.clamp;

public class GbAnchor {

    private final UUID owner;
    private final Vec3 pos;
    private final EntityRef<Display.BlockDisplay> displayRef;
    private int charges = 0;

    public GbAnchor(UUID owner, Vec3 pos, Display.BlockDisplay display) {
        this.owner = owner;
        this.pos = pos;
        this.displayRef = new EntityRef<>(display);
    }

    @Nullable
    private Display.BlockDisplay display() {
        return displayRef.resolve();
    }

    public int charges() {
        return charges;
    }

    public Vec3 pos() {
        return pos;
    }

    public UUID owner() {
        return owner;
    }

    public void setCharges(int charges) {
        this.charges = clamp(charges, 0, 4);

        var display = display();

        if (display == null) return;

        DisplayEntityAccess.setBlockState(display, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, this.charges));
    }

    public void discard() {
        var display = display();

        if (display != null) {
            display.discard();
        }
    }
}
