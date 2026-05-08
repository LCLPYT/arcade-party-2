package work.lclpnet.ap2.game.knockout.util;

import com.google.common.collect.Iterables;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.gaco.collisions.util.PlayerAction;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ImpactDetector {

    private static final boolean DEBUG_IMPACT = false;

    private final Participants participants;
    private final DebugController debugController;
    private final double thresholdSpeed;
    private final Hook<OnImpact> onImpact = HookFactory.createArrayBacked(OnImpact.class, hooks -> (player, collisions) -> {
        for (var hook : hooks) {
            hook.onImpact(player, collisions);
        }
    });
    private final Hook<PlayerAction> onMiss = HookFactory.createArrayBacked(PlayerAction.class, hooks -> (player) -> {
        for (var hook : hooks) {
            hook.act(player);
        }
    });
    private final Map<UUID, Entry> entries = new HashMap<>();

    public ImpactDetector(Participants participants, DebugController debugController, double thresholdSpeed) {
        this.participants = participants;
        this.debugController = debugController;
        this.thresholdSpeed = thresholdSpeed;
    }

    public void enable(TaskScheduler scheduler) {
        scheduler.interval(this::tick, 1);
    }

    public Hook<OnImpact> onImpact() {
        return onImpact;
    }

    public Hook<PlayerAction> onMiss() {
        return onMiss;
    }

    private void tick() {
        entries.entrySet().removeIf(entry -> !participants.isParticipating(entry.getKey()));

        for (ServerPlayer player : participants) {
            checkImpact(player);
        }
    }

    public void checkImpact(ServerPlayer player) {
        Entry entry = entry(player);
        Vec3 prevPos = entry.pos;
        Vec3 currentPos = player.position();

        entry.pos = currentPos;

        if (prevPos == null) return;

        Vec3 velocity = currentPos.subtract(prevPos);

        if (velocity.lengthSqr() > 1e-5) {
            checkImpact(player, velocity);
        }
    }

    public void checkImpact(ServerPlayer player, Vec3 velocity) {
        Entry entry = entry(player);
        double prevSpeed = entry.speed;
        double speed = velocity.length();

        entry.speed = speed;
        entry.velocity = velocity;

        if (speed < thresholdSpeed) {
            if (prevSpeed >= thresholdSpeed) {
                onMiss.invoker().act(player);
            }
            return;
        }

        // predict future horizontal position
        Vec3 dir = velocity.multiply(1.0, 0.0, 1.0).scale(1.d / speed);
        Vec3 pos = player.position();
        Vec3 futurePos1 = pos.add(dir.scale(0.2)).add(0, 0.01, 0);
        Vec3 futurePos2 = pos.add(dir.scale(0.4)).add(0, 0.01, 0);

        EntityDimensions pose = player.getDimensions(player.getPose());
        AABB futureBox1 = pose.makeBoundingBox(futurePos1);
        AABB futureBox2 = pose.makeBoundingBox(futurePos2);

        if (DEBUG_IMPACT) {
            debugController.exclusive("box_" + player.getScoreboardName(), controller -> controller.renderer().ifPresent(r -> {
                r.marker(pos, Blocks.LIME_TERRACOTTA.defaultBlockState(), 0x06cc34);
                r.box(futureBox1, Blocks.LIME_STAINED_GLASS.defaultBlockState());
                r.box(futureBox2, Blocks.LIME_STAINED_GLASS.defaultBlockState());
                r.text(pos.add(0, 0.25, 0), Component.literal(String.format("%.3f", speed)));
                r.arrow(pos, dir, Blocks.BLUE_CONCRETE.defaultBlockState());
            }));
        }

        var collisions = Iterables.concat(collisions(player, futureBox1), collisions(player, futureBox2));
        var it = collisions.iterator();

        if (!it.hasNext()) return;

        onImpact.invoker().onImpact(player, collisions);
    }

    private @NotNull Entry entry(ServerPlayer player) {
        return entries.computeIfAbsent(player.getUUID(), _ -> new Entry());
    }

    private Iterable<BlockPos> collisions(ServerPlayer player, AABB box) {
        // refer to CollisionView::getBlockCollisions
        return () -> new BlockCollisions<>(player.level(), player, box, false, (pos, _) -> pos);
    }

    public @Nullable Vec3 getVelocity(ServerPlayer player) {
        Entry entry = entries.get(player.getUUID());

        if (entry == null) {
            return null;
        }

        return entry.velocity;
    }

    private static class Entry {
        Vec3 pos = null;
        Vec3 velocity = null;
        double speed = 0.d;
    }

    public interface OnImpact {
        void onImpact(ServerPlayer player, Iterable<BlockPos> collisions);
    }
}
