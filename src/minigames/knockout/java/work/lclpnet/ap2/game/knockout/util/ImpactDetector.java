package work.lclpnet.ap2.game.knockout.util;

import com.google.common.collect.Iterables;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockCollisionSpliterator;
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

        for (ServerPlayerEntity player : participants) {
            checkImpact(player);
        }
    }

    public void checkImpact(ServerPlayerEntity player) {
        Entry entry = entry(player);
        Vec3d prevPos = entry.pos;
        Vec3d currentPos = player.getEntityPos();

        entry.pos = currentPos;

        if (prevPos == null) return;

        Vec3d velocity = currentPos.subtract(prevPos);

        if (velocity.lengthSquared() > 1e-5) {
            checkImpact(player, velocity);
        }
    }

    public void checkImpact(ServerPlayerEntity player, Vec3d velocity) {
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
        Vec3d dir = velocity.multiply(1.0, 0.0, 1.0).multiply(1.d / speed);
        Vec3d pos = player.getEntityPos();
        Vec3d futurePos1 = pos.add(dir.multiply(0.2)).add(0, 0.01, 0);
        Vec3d futurePos2 = pos.add(dir.multiply(0.4)).add(0, 0.01, 0);

        EntityDimensions pose = player.getDimensions(player.getPose());
        Box futureBox1 = pose.getBoxAt(futurePos1);
        Box futureBox2 = pose.getBoxAt(futurePos2);

        if (DEBUG_IMPACT) {
            debugController.exclusive("box_" + player.getNameForScoreboard(), controller -> controller.renderer().ifPresent(r -> {
                r.marker(pos, Blocks.LIME_TERRACOTTA.getDefaultState(), 0x06cc34);
                r.box(futureBox1, Blocks.LIME_STAINED_GLASS.getDefaultState());
                r.box(futureBox2, Blocks.LIME_STAINED_GLASS.getDefaultState());
                r.text(pos.add(0, 0.25, 0), Text.literal(String.format("%.3f", speed)));
                r.arrow(pos, dir, Blocks.BLUE_CONCRETE.getDefaultState());
            }));
        }

        var collisions = Iterables.concat(collisions(player, futureBox1), collisions(player, futureBox2));
        var it = collisions.iterator();

        if (!it.hasNext()) return;

        onImpact.invoker().onImpact(player, collisions);
    }

    private @NotNull Entry entry(ServerPlayerEntity player) {
        return entries.computeIfAbsent(player.getUuid(), u -> new Entry());
    }

    private Iterable<BlockPos> collisions(ServerPlayerEntity player, Box box) {
        // refer to CollisionView::getBlockCollisions
        return () -> new BlockCollisionSpliterator<>(player.getEntityWorld(), player, box, false, (pos, voxelShape) -> pos);
    }

    public @Nullable Vec3d getVelocity(ServerPlayerEntity player) {
        Entry entry = entries.get(player.getUuid());

        if (entry == null) {
            return null;
        }

        return entry.velocity;
    }

    private static class Entry {
        Vec3d pos = null;
        Vec3d velocity = null;
        double speed = 0.d;
    }

    public interface OnImpact {
        void onImpact(ServerPlayerEntity player, Iterable<BlockPos> collisions);
    }
}
