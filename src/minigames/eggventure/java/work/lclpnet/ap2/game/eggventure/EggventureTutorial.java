package work.lclpnet.ap2.game.eggventure;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.impl.scene.MixedMountContext;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.animation.Animatable;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.object.ItemDisplayObject;
import work.lclpnet.ap2.impl.scene.object.PlayerTextDisplayObject;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.ap2.impl.util.world.WorldPosSync;
import work.lclpnet.gaco.dynamic_entities.DynamicEntity;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.GREEN;

public class EggventureTutorial {

    private static final int
            DURATION_TICKS = Ticks.seconds(4),
            EGG_SWITCH_TICKS = 10;

    private final ServerWorld world;
    private final Scene scene;
    private final Random random;
    private final Translations translations;
    private final Collection<TutorialEgg> eggs = new ArrayList<>();
    private final List<PlayerHead> variants;

    public EggventureTutorial(ServerWorld world, DynamicEntityManager dynamicEntityManager, Random random, Translations translations) {
        this.world = world;
        this.random = random;
        this.translations = translations;

        scene = new Scene(new MixedMountContext(world, dynamicEntityManager));
        variants = EggventureInstance.eggVariants(world.getRegistryManager());
    }

    public CompletableFuture<Void> start(TaskScheduler scheduler, Participants participants) {
        if (variants.isEmpty()) {
            throw new IllegalStateException("There are no egg variants defined");
        }

        for (ServerPlayerEntity player : participants) {
            PlayerHead variant = variants.get(random.nextInt(variants.size()));

            startTutorial(player, variant);
        }

        scene.animate(1, scheduler);

        var future = new CompletableFuture<Void>();

        var switcher = scheduler.interval(new Runnable() {
            int t = 0;

            @Override
            public void run() {
                if (++t % EGG_SWITCH_TICKS == 0) {
                    switchEggVariants();
                }
            }
        }, 1);

        scheduler.timeout(() -> {
            switcher.cancel();

            scene.clear();
            scene.stopAnimation();

            future.complete(null);
        }, DURATION_TICKS);

        return future;
    }

    private void switchEggVariants() {
        for (TutorialEgg egg : eggs) {
            PlayerHead variant = variants.get(random.nextInt(variants.size()));
            egg.setStack(variant.createStack());
        }
    }

    private void startTutorial(ServerPlayerEntity player, PlayerHead variant) {
        UUID uuid = player.getUuid();
        var text = translations.translateText(player, "game.ap2.eggventure.find_sample").formatted(GREEN);

        var egg = new TutorialEgg(scene, variant, () -> world.getServer().getPlayerManager().getPlayer(uuid));
        var label = new PlayerTextDisplayObject(scene, text, player);
        label.position.set(0, 0.1, 0);
        label.scale.set(0.65);
        label.setBillboardMode(DisplayEntity.BillboardMode.CENTER);

        egg.addChild(label);

        scene.add(egg);

        eggs.add(egg);
    }

    private static class TutorialEgg extends ItemDisplayObject implements DynamicEntity, Animatable {

        private static final double
                PLAYER_DIST = 2.5,
                EGG_RADIUS = 0.25;

        private final Resolvable<ServerPlayerEntity> playerRef;
        private final WorldPosSync posSync = new WorldPosSync();

        public TutorialEgg(Scene scene, PlayerHead variant, Resolvable<ServerPlayerEntity> playerRef) {
            super(scene, variant.createStack());
            this.playerRef = playerRef;
        }

        @Override
        public void updateMatrixWorld(boolean withParent, boolean withChildren) {
            super.updateMatrixWorld(withParent, withChildren);

            posSync.update(matrixWorld);
        }

        @Override
        public Vec3d getPosition() {
            return posSync.mcWorldPos();
        }

        @Override
        public @Nullable Entity getEntity(ServerPlayerEntity player) {
            ServerPlayerEntity owner = playerRef.optional().orElse(null);

            if (owner == null || player != owner) {
                return null;
            }

            return entityRef.resolve();
        }

        @Override
        public void cleanup(ServerPlayerEntity player) {
            // no need to clean the entity really, as the object should already have been removed
        }

        @Override
        public void updateAnimation(double dt, AnimationContext ctx) {
            ServerPlayerEntity player = playerRef.optional().orElse(null);

            if (player == null) return;

            HitResult hit = RayCastUtil.raycast(
                    player.getWorld(), player.getEyePos(), player.getRotationVector(), PLAYER_DIST,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, ShapeContext.absent(),
                    entity -> !entity.isSpectator());

            Vec3d pos = hit.getPos();

            if (hit instanceof BlockHitResult blockHit) {
                pos = pos.add(blockHit.getSide().getDoubleVector().multiply(EGG_RADIUS));
            } else if (hit.getType() != HitResult.Type.MISS) {
                pos = pos.add(player.getRotationVector().multiply(-EGG_RADIUS));
            }

            position.set(pos.getX(), pos.getY() + EGG_RADIUS, pos.getZ());
        }
    }
}
