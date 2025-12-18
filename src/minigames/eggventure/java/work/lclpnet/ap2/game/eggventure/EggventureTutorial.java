package work.lclpnet.ap2.game.eggventure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.impl.util.RayCastUtil;
import work.lclpnet.gaco.core.api.Resolvable;
import work.lclpnet.gaco.dynamic_entities.DynamicEntity;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;
import work.lclpnet.gaco.scene.MixedMountContext;
import work.lclpnet.gaco.scene.Scene;
import work.lclpnet.gaco.scene.animation.Animatable;
import work.lclpnet.gaco.scene.animation.AnimationContext;
import work.lclpnet.gaco.scene.object.ItemDisplayObject;
import work.lclpnet.gaco.scene.object.PlayerTextDisplayObject;
import work.lclpnet.gaco.scene.util.WorldPosSync;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.ChatFormatting.GREEN;

public class EggventureTutorial {

    private static final int
            DURATION_TICKS = Ticks.seconds(4),
            EGG_SWITCH_TICKS = 10;

    private final ServerLevel world;
    private final Scene scene;
    private final Random random;
    private final Translations translations;
    private final Collection<TutorialEgg> eggs = new ArrayList<>();
    private final List<PlayerHead> variants;

    public EggventureTutorial(ServerLevel world, DynamicEntityManager dynamicEntityManager, Random random, Translations translations) {
        this.world = world;
        this.random = random;
        this.translations = translations;

        scene = new Scene(new MixedMountContext(world, dynamicEntityManager));
        variants = EggventureInstance.eggVariants(world.registryAccess());
    }

    public CompletableFuture<Void> start(TaskScheduler scheduler, Participants participants) {
        if (variants.isEmpty()) {
            throw new IllegalStateException("There are no egg variants defined");
        }

        for (ServerPlayer player : participants) {
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

    private void startTutorial(ServerPlayer player, PlayerHead variant) {
        UUID uuid = player.getUUID();
        var text = translations.translateText(player, "game.ap2.eggventure.find_sample").formatted(GREEN);

        var egg = new TutorialEgg(scene, variant, () -> world.getServer().getPlayerList().getPlayer(uuid));
        var label = new PlayerTextDisplayObject(scene, text, player);
        label.position.set(0, 0.1, 0);
        label.scale.set(0.65);
        label.setBillboardMode(Display.BillboardConstraints.CENTER);

        egg.addChild(label);

        scene.add(egg);

        eggs.add(egg);
    }

    private static class TutorialEgg extends ItemDisplayObject implements DynamicEntity, Animatable {

        private static final double
                PLAYER_DIST = 2.5,
                EGG_RADIUS = 0.25;

        private final Resolvable<ServerPlayer> playerRef;
        private final WorldPosSync posSync = new WorldPosSync();

        public TutorialEgg(Scene scene, PlayerHead variant, Resolvable<ServerPlayer> playerRef) {
            super(scene, variant.createStack());
            this.playerRef = playerRef;
        }

        @Override
        public void updateMatrixWorld(boolean withParent, boolean withChildren) {
            super.updateMatrixWorld(withParent, withChildren);

            posSync.update(matrixWorld);
        }

        @Override
        public Vec3 getPosition() {
            return posSync.mcWorldPos();
        }

        @Override
        public @Nullable Entity getEntity(ServerPlayer player) {
            ServerPlayer owner = playerRef.optional().orElse(null);

            if (owner == null || player != owner) {
                return null;
            }

            return entityRef.resolve();
        }

        @Override
        public void cleanup(ServerPlayer player) {
            // no need to clean the entity really, as the object should already have been removed
        }

        @Override
        public void updateAnimation(double dt, AnimationContext ctx) {
            ServerPlayer player = playerRef.optional().orElse(null);

            if (player == null) return;

            HitResult hit = RayCastUtil.raycast(
                    player.level(), player.getEyePosition(), player.getLookAngle(), PLAYER_DIST,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, CollisionContext.empty(),
                    entity -> !entity.isSpectator());

            Vec3 pos = hit.getLocation();

            if (hit instanceof BlockHitResult blockHit) {
                pos = pos.add(blockHit.getDirection().getUnitVec3().scale(EGG_RADIUS));
            } else if (hit.getType() != HitResult.Type.MISS) {
                pos = pos.add(player.getLookAngle().scale(-EGG_RADIUS));
            }

            position.set(pos.x(), pos.y() + EGG_RADIUS, pos.z());
        }
    }
}
