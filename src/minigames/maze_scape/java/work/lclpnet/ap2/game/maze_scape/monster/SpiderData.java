package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.AccelerationBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.SameRoomBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.UnstuckBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.ValidPositionBehaviour;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.List;
import java.util.Random;

public class SpiderData implements MonsterData<Spider> {

    private static final int
            COBWEB_DELAY_MIN_TICKS = Ticks.seconds(6),
            COBWEB_DELAY_MAX_TICKS = Ticks.seconds(20),
            COBWEB_SPECIAL_TICKS = Ticks.seconds(20);

    private final CommonData common;
    private final Random random;
    private int nextCobweb;

    public SpiderData(MonsterArgs args, Random random) {
        this.common = new CommonData(args, List.of(
                new ValidPositionBehaviour(args.manager(), args.logger()),
                new AccelerationBehaviour(0.35, 0.48),
                new UnstuckBehaviour(args.manager(), 0.65),
                new SameRoomBehaviour<>(args.manager().struct(), COBWEB_SPECIAL_TICKS, this::cobwebSpecial)
        ));

        this.random = random;

        scheduleCobweb();
    }

    private void scheduleCobweb() {
        nextCobweb = COBWEB_DELAY_MIN_TICKS + random.nextInt(COBWEB_DELAY_MAX_TICKS - COBWEB_DELAY_MIN_TICKS + 1);
    }

    @Override
    public void init(Spider spider) {
        common.init(spider);
    }

    @Override
    public void tick(Spider spider) {
        common.tick(spider);

        if (nextCobweb-- <= 0) {
            placeCobweb();
            scheduleCobweb();
        }
    }

    @Override
    public void onKillAcquired(Spider spider) {
        common.onKillAcquired(spider);
    }

    @Override
    public @Nullable Spider mob() {
        if (common.mob() instanceof Spider spider) {
            return spider;
        }

        return null;
    }

    private void cobwebSpecial(Spider spider, LivingEntity target) {
        putCobweb(target.blockPosition());
        target.hurtServer(common.manager().world(), spider.damageSources().indirectMagic(spider, spider), 2);
        target.addEffect(new MobEffectInstance(MobEffects.POISON, Ticks.seconds(5), 0));
    }

    private void placeCobweb() {
        Spider spider = mob();

        if (spider == null) return;

        putCobweb(spider.blockPosition());
    }

    private void putCobweb(BlockPos blockPos) {
        ServerLevel world = common.manager().world();

        if (!world.getBlockState(blockPos).isAir()) return;

        world.setBlockAndUpdate(blockPos, Blocks.COBWEB.defaultBlockState());
    }
}
