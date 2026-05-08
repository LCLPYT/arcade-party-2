package work.lclpnet.ap2.game.tnt_run;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.gaco.collisions.util.GroundDetector;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.level.BlockBreakParticleCallback;

import java.util.ArrayList;
import java.util.List;

public class TntRunInstance extends EliminationGameInstance {

    private static final BlockState MARKED_STATE = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final double BLOCK_MARGIN = 0.35;
    private static final int BREAK_TICKS = 10;
    private final Object2IntMap<BlockPos> removal = new Object2IntOpenHashMap<>();
    private final List<BlockPos> groundBlocks = new ArrayList<>();
    private GroundDetector groundDetector = null;

    public TntRunInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();

        HookRegistrar hooks = gameHandle.getHooks();

        BlockBreakParticleCallback.HOOK.registerWith(hooks, (_, _, _) -> true);
    }

    @Override
    protected void go() {
        groundDetector = new GroundDetector(getWorld(), BLOCK_MARGIN);

        commons().whenBelowCriticalHeight().then(this::eliminate);

        gameHandle.getScheduler().interval(this::tick, 1);
    }

    private void tick() {
        tickRemoval();

        Participants participants = gameHandle.getParticipants();

        groundBlocks.clear();

        for (ServerPlayer player : participants) {
            groundDetector.collectBlocksBelow(player, groundBlocks);
        }

        markForRemovalBelow();
    }

    private void tickRemoval() {
        ServerLevel world = getWorld();
        int flags = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

        var it = removal.object2IntEntrySet().iterator();

        while (it.hasNext()) {
            var entry = it.next();
            int remain = entry.getIntValue();

            if (remain > 0) {
                entry.setValue(remain - 1);
                continue;
            }

            BlockPos key = entry.getKey();
            it.remove();
            world.setBlock(key, Blocks.AIR.defaultBlockState(), flags);
        }
    }

    private void markForRemovalBelow() {
        ServerLevel world = getWorld();

        for (BlockPos pos : groundBlocks) {
            if (removal.containsKey(pos)) continue;

            BlockPos posUp = pos.above();

            BlockState state = world.getBlockState(pos);

            if (state.isAir()) continue;

            BlockState above = world.getBlockState(posUp);
            VoxelShape aboveShape = above.getCollisionShape(world, posUp, CollisionContext.empty());

            // don't remove walls
            if (!aboveShape.isEmpty()) {
                AABB box = aboveShape.bounds();

                if (box.getXsize() >= 1 && box.getZsize() >= 1) continue;
            }

            removal.put(pos, BREAK_TICKS);

            if (state.getCollisionShape(world, pos).isEmpty()) continue;

            AABB markedCollisionBox = MARKED_STATE.getCollisionShape(world, pos).bounds().move(pos);
            List<Entity> colliding = world.getEntities((Entity) null, markedCollisionBox, entity -> !entity.isSpectator());

            for (Entity entity : colliding) {
                double dy = markedCollisionBox.maxY - entity.getY();
                entity.teleportTo(world, 0, dy, 0, Relative.ALL, 0, 0, false);
            }

            world.setBlockAndUpdate(pos, MARKED_STATE);
        }
    }
}
