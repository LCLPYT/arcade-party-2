package work.lclpnet.ap2.game.maze_scape.monster;

import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.ap2.core.mixin.MobAccessor;
import work.lclpnet.ap2.core.mixin.PathNavigationAccessor;
import work.lclpnet.ap2.core.type.*;
import work.lclpnet.ap2.game.maze_scape.ai.AttackGoal;
import work.lclpnet.ap2.game.maze_scape.ai.MoveToTargetGoal;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.PitPathFindingPredicate;
import work.lclpnet.ap2.game.maze_scape.util.RandomGenerator;
import work.lclpnet.ap2.game.maze_scape.util.TrapdoorPathFindingPredicate;
import work.lclpnet.ap2.impl.ai.BlockedPathFindingPredicate;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.ap2.impl.util.GoalModifier;
import work.lclpnet.gaco.core.api.Partial;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.minecraft.world.entity.ai.attributes.Attributes.*;

public class MonsterSpawner {

    private static final boolean DEBUG_SHOW_MOBS = false;

    private final MSManager manager;
    private final Logger logger;
    private final Random random;
    private final ServerLevel world;

    public MonsterSpawner(MSManager manager, Logger logger, Random random) {
        this.manager = manager;
        this.logger = logger;
        this.random = random;

        world = manager.world();
    }

    public void spawn(RandomGenerator<Vec3> spawns, Registrar consumer) {
        Partial<MonsterArgs, UUID> args = uuid -> new MonsterArgs(uuid, manager, logger);

        List<MonsterFactory> primary = new ArrayList<>();
        primary.add(this::spawnWarden);
        primary.add(this::spawnSpider);

        List<MonsterFactory> secondary = new ArrayList<>();
        secondary.add(this::spawnEnderman);
        secondary.add(this::spawnCreaking);

        final int players = manager.participants().count();

        int primaryMobs = max(1, min(primary.size(), players / 2));
        int secondaryMobs = max(1, min(secondary.size(), players / 3));

        for (int i = 0; i < primaryMobs && !primary.isEmpty(); i++) {
            var factory = primary.remove(random.nextInt(primary.size()));
            factory.spawn(spawns.get(), args, consumer);
        }

        for (int i = 0; i < secondaryMobs && !secondary.isEmpty(); i++) {
            var factory = secondary.remove(random.nextInt(secondary.size()));
            factory.spawn(spawns.get(), args, consumer);
        }
    }

    private void spawnWarden(Vec3 pos, Partial<MonsterArgs, UUID> args, Registrar registrar) {
        var warden = new Warden(EntityType.WARDEN, world);

        configureMobCommon(pos, warden);

        EntityUtil.setAttribute(warden, ATTACK_DAMAGE, 10);

        var brain = warden.getBrain();
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.EMERGE, 5, ImmutableList.of(), MemoryModuleType.IS_EMERGING);
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.DIG, 5, ImmutableList.of(), MemoryModuleType.DIG_COOLDOWN);
        brain.useDefaultActivity();

        world.addFreshEntity(warden);

        UUID uuid = warden.getUUID();
        var data = new WardenData(args.with(uuid));

        registrar.accept(uuid, data);
    }

    @SuppressWarnings("DataFlowIssue")
    private void spawnSpider(Vec3 pos, Partial<MonsterArgs, UUID> args, Registrar registrar) {
        var spider = new Spider(EntityType.SPIDER, world);

        configureMobCommon(pos, spider);

        EntityUtil.setAttribute(spider, ATTACK_DAMAGE, 5);

        ((ApSpider) spider).ap2$setCanClimb(false);

        GoalSelector goalSelector = resetAi(spider).getGoalSelector();

        goalSelector.addGoal(1, new FloatGoal(spider));
        goalSelector.addGoal(3, new LeapAtTargetGoal(spider, 0.4f));
        goalSelector.addGoal(4, new MoveToTargetGoal(spider, 1.0));
        goalSelector.addGoal(4, new AttackGoal(spider));
        goalSelector.addGoal(6, new LookAtPlayerGoal(spider, Player.class, 8.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(spider));

        world.addFreshEntity(spider);

        UUID uuid = spider.getUUID();
        var data = new SpiderData(args.with(uuid), random);

        registrar.accept(uuid, data);
    }

    private void spawnEnderman(Vec3 pos, Partial<MonsterArgs, UUID> args, Registrar registrar) {
        var enderman = new EnderMan(EntityType.ENDERMAN, world);

        configureMobCommon(pos, enderman);

        EntityUtil.setAttribute(enderman, ATTACK_DAMAGE, 20);
        enderman.setSilent(true);

        UUID uuid = enderman.getUUID();
        var data = new EndermanData(args.with(uuid), manager.struct());

        GoalSelector goalSelector = resetAi(enderman).getGoalSelector();

        goalSelector.addGoal(0, new FloatGoal(enderman));
        goalSelector.addGoal(4, new MoveToTargetGoal(enderman, 1.0, data::targetPos));
        goalSelector.addGoal(4, new AttackGoal(enderman));
        goalSelector.addGoal(6, new RandomLookAroundGoal(enderman));

        world.addFreshEntity(enderman);

        registrar.accept(uuid, data);
    }

    private void spawnCreaking(Vec3 pos, Partial<MonsterArgs, UUID> args, Registrar registrar) {
        var creaking = new Creaking(EntityType.CREAKING, world);

        configureMobCommon(pos, creaking);

        EntityUtil.setAttribute(creaking, ATTACK_DAMAGE, 12);
        EntityUtil.setAttribute(creaking, MOVEMENT_SPEED, 0.5);

        world.addFreshEntity(creaking);

        UUID uuid = creaking.getUUID();
        var data = new CreakingData(args.with(uuid));

        registrar.accept(uuid, data);
    }

    private static @NotNull MobAccessor resetAi(Mob mob) {
        var access = (MobAccessor) mob;

        GoalModifier.clear(access.getGoalSelector());
        GoalModifier.clear(access.getTargetSelector());

        return access;
    }

    private void configureMobCommon(Vec3 pos, Mob entity) {
        entity.setPos(pos);
        entity.setInvulnerable(true);
        entity.setPersistenceRequired();
        entity.setOnGround(true);  // required to perform path finding immediately

        float maxWidth = 0.62f;

        if (entity.getBbWidth() > maxWidth) {
            ((ApLivingEntity) entity).ap2$setServerSidedScale(maxWidth / entity.getBbWidth());
            entity.refreshDimensions();
        }

        if (DEBUG_SHOW_MOBS) {
            entity.setGlowingTag(true);
        }

        EntityUtil.setAttribute(entity, STEP_HEIGHT, 2);

        PathNavigation navigation = entity.getNavigation();
        navigation.setMaxVisitedNodesMultiplier(1f);

        if (navigation instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanWalkOverFences(true);
        }

        if (navigation instanceof ApMobNavigation nav) {
            nav.ap2$patchTrapdoorPathFindingTarget();
        }

        ApEntity apEntity = (ApEntity) entity;

        // fix warden getting stuck on narrow blocks, like open trapdoors on walls / as railings
        apEntity.ap2$patchNarrowMovement();

        // fix continuous jumping when walking by open trapdoors
        apEntity.ap2$patchTrapdoorJumping();

        // adjust PathNodeMaker
        NodeEvaluator nodeMaker = ((PathNavigationAccessor) navigation).getNodeEvaluator();

        if (nodeMaker instanceof ApLandPathNodeMaker apPathMaker) {
            apPathMaker.ap2$addCustomBlockedPredicate(BlockedPathFindingPredicate.getInstance());
            apPathMaker.ap2$addCustomBlockedPredicate(new PitPathFindingPredicate(manager.struct()));
            apPathMaker.ap2$addCustomInvalidPredicate(TrapdoorPathFindingPredicate.getInstance());
        }
    }

    private interface MonsterFactory {
        void spawn(Vec3 pos, Partial<MonsterArgs, UUID> args, Registrar registrar);
    }

    public interface Registrar extends BiConsumer<UUID, MonsterData<?>> {}
}
