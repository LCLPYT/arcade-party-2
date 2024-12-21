package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Random;
import java.util.UUID;

public class SpiderData implements MonsterData {

    private static final int
            COBWEB_DELAY_MIN_TICKS = Ticks.seconds(6),
            COBWEB_DELAY_MAX_TICKS = Ticks.seconds(20),
            COBWEB_SPECIAL_TICKS = Ticks.seconds(20);

    private final CommonData common;
    private final Random random;
    private int nextCobweb;

    public SpiderData(UUID uuid, MSManager manager, Logger logger, Random random) {
        this.common = new CommonData(uuid, manager, logger, 0.3, 0.48, 0.125);
        this.random = random;

        scheduleCobweb();
    }

    private void scheduleCobweb() {
        nextCobweb = COBWEB_DELAY_MIN_TICKS + random.nextInt(COBWEB_DELAY_MAX_TICKS - COBWEB_DELAY_MIN_TICKS + 1);
    }

    @Override
    public void init() {
        common.init();
    }

    @Override
    public void tick() {
        common.tick();

        if (nextCobweb-- <= 0) {
            placeCobweb();
            scheduleCobweb();
        }

        if (common.sameRoomTimerDue(COBWEB_SPECIAL_TICKS)) {
            cobwebSpecial();
        }
    }

    @Override
    public void onKillAcquired() {
        common.onKillAcquired();
    }

    public @Nullable SpiderEntity spider() {
        if (common.mob() instanceof SpiderEntity spider) {
            return spider;
        }

        return null;
    }

    private void cobwebSpecial() {
        SpiderEntity spider = spider();

        if (spider == null) return;

        LivingEntity target = spider.getTarget();

        if (target == null) return;

        putCobweb(target.getBlockPos());
    }

    private void placeCobweb() {
        SpiderEntity spider = spider();

        if (spider == null) return;

        putCobweb(spider.getBlockPos());
    }

    private void putCobweb(BlockPos blockPos) {
        ServerWorld world = common.manager().world();

        if (!world.getBlockState(blockPos).isAir()) return;

        world.setBlockState(blockPos, Blocks.COBWEB.getDefaultState());
    }
}
