package work.lclpnet.ap2.core.patch;

import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import work.lclpnet.lobby.util.RayCaster;

import static java.lang.Math.*;
import static net.minecraft.block.HorizontalFacingBlock.FACING;
import static net.minecraft.block.TrapdoorBlock.OPEN;

public class TrapdoorJumpPatch {

    private TrapdoorJumpPatch() {}

    public static boolean preventJumping(BlockState state) {
        return state.isIn(BlockTags.TRAPDOORS) && state.contains(OPEN) && state.get(OPEN);
    }

    public static boolean shouldJump(MobEntity entity) {
        if (!entity.getNavigation().isFollowingPath()) return false;

        MoveControl moveControl = entity.getMoveControl();
        double tx = moveControl.getTargetX();
        double tz = moveControl.getTargetZ();

        Vec3d target = new Vec3d(tx, entity.getY(), tz);
        Vec3d start = entity.getPos();

        Vector3f dir = target.subtract(start).toVector3f().normalize();

        World world = entity.getWorld();

        BlockHitResult result = RayCaster.rayCast(start, target, pos -> {
            BlockState state = world.getBlockState(pos);

            if (!state.isIn(BlockTags.TRAPDOORS) || !state.contains(FACING) || !state.contains(OPEN) || !state.get(OPEN)) {
                return false;
            }

            Direction facing = state.get(FACING);
            float dot = facing.getUnitVector().dot(dir);

            return abs(dot) >= cos(PI / 4);
        });

        return result.getType() == HitResult.Type.BLOCK;
    }
}
