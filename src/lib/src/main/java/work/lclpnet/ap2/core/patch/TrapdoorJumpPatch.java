package work.lclpnet.ap2.core.patch;

import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import work.lclpnet.game.util.RayCaster;

import static java.lang.Math.*;
import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
import static net.minecraft.world.level.block.TrapDoorBlock.OPEN;

public class TrapdoorJumpPatch {

    private TrapdoorJumpPatch() {}

    public static boolean preventJumping(BlockState state) {
        return state.is(BlockTags.TRAPDOORS) && state.hasProperty(OPEN) && state.getValue(OPEN);
    }

    public static boolean shouldJump(Mob entity) {
        if (!entity.getNavigation().isInProgress()) return false;

        MoveControl moveControl = entity.getMoveControl();
        double tx = moveControl.getWantedX();
        double tz = moveControl.getWantedZ();

        Vec3 target = new Vec3(tx, entity.getY(), tz);
        Vec3 start = entity.position();

        Vector3f dir = target.subtract(start).toVector3f().normalize();

        Level world = entity.level();

        BlockHitResult result = RayCaster.rayCast(start, target, pos -> {
            BlockState state = world.getBlockState(pos);

            if (!state.is(BlockTags.TRAPDOORS) || !state.hasProperty(FACING) || !state.hasProperty(OPEN) || !state.getValue(OPEN)) {
                return false;
            }

            Direction facing = state.getValue(FACING);
            float dot = facing.step().dot(dir);

            return abs(dot) >= cos(PI / 4);
        });

        return result.getType() == HitResult.Type.BLOCK;
    }
}
