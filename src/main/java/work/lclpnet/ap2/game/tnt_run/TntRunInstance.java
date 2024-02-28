package work.lclpnet.ap2.game.tnt_run;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.kibu.hook.world.BlockBreakParticleCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

public class TntRunInstance extends EliminationGameInstance {

    private static final BlockState MARKED_STATE = Blocks.RED_TERRACOTTA.getDefaultState();
    private static final double BLOCK_MARGIN = 0.35;
    private static final int BREAK_TICKS = 10;
    private final Object2IntMap<BlockPos> removal = new Object2IntOpenHashMap<>();

    public TntRunInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(BlockBreakParticleCallback.HOOK, (world, pos, state) -> true);
    }

    @Override
    protected void ready() {
        commons().whenBelowCriticalHeight().then(this::eliminate);

        gameHandle.getGameScheduler().interval(this::tick, 1);
    }

    private void tick() {
        tickRemoval();

        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : participants) {
            markForRemovalBelow(player);
        }
    }

    private void tickRemoval() {
        ServerWorld world = getWorld();
        int flags = Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS;

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
            world.setBlockState(key, Blocks.AIR.getDefaultState(), flags);
        }
    }

    private void markForRemovalBelow(ServerPlayerEntity player) {
        ServerWorld world = getWorld();

        double x = player.getX(), y = player.getY() - 1, z = player.getZ();
        var pos = new BlockPos.Mutable();

        for (int dx = -1; dx < 2; dx++) {
            for (int dz = -1; dz < 2; dz++) {
                pos.set(x + BLOCK_MARGIN * dx, y, z + BLOCK_MARGIN * dz);

                BlockState state = world.getBlockState(pos);

                if (state.isAir() || removal.containsKey(pos)) continue;

                // mark for removal
                removal.put(pos.toImmutable(), BREAK_TICKS);

                if (!state.getCollisionShape(world, pos).isEmpty()) {
                    world.setBlockState(pos, MARKED_STATE);
                }
            }
        }
    }
}
