package work.lclpnet.ap2.game.knockout.util;

import com.google.common.collect.Iterables;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockCollisionSpliterator;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.util.action.PlayerAction;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ImpactDetector {

    private static final boolean DEBUG_IMPACT = false;

    private final Participants participants;
    private final DebugController debugController;
    private final double threshold, thresholdSq;
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
    private Set<UUID> inertia = new HashSet<>();

    public ImpactDetector(Participants participants, DebugController debugController, double threshold) {
        this.participants = participants;
        this.debugController = debugController;
        this.threshold = threshold;
        this.thresholdSq = threshold * threshold;
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
        inertia.removeIf(uuid -> !participants.isParticipating(uuid));

        for (ServerPlayerEntity player : participants) {
            checkImpact(player);
        }
    }

    public void checkImpact(ServerPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        double magSq = velocity.lengthSquared();

        if (magSq < thresholdSq) {
            if (inertia.remove(player.getUuid())) {
                onMiss.invoker().act(player);
            }

            return;
        }

        inertia.add(player.getUuid());

        double mag = Math.sqrt(magSq);

        if (Double.isNaN(mag)) return;

        // predict future position
        Vec3d dir = velocity.multiply(1.d / mag);

        double offset = 0.3 + (mag - threshold) * 0.2;

        Vec3d futurePos1 = player.getPos().add(dir.multiply(offset));
        Vec3d futurePos2 = player.getPos().add(dir.multiply(offset * 2));
        Box futureBox1 = player.getDimensions(player.getPose()).getBoxAt(futurePos1).expand(0.1);
        Box futureBox2 = player.getDimensions(player.getPose()).getBoxAt(futurePos2).expand(0.1);

        if (DEBUG_IMPACT) {
            debugController.exclusive("box_" + player.getNameForScoreboard(), controller -> controller.renderer().ifPresent(r -> {
                r.marker(player.getPos(), Blocks.LIME_TERRACOTTA.getDefaultState(), 0x06cc34);
                r.box(futureBox1, Blocks.LIME_STAINED_GLASS.getDefaultState());
                r.box(futureBox2, Blocks.LIME_STAINED_GLASS.getDefaultState());
                r.text(player.getPos().add(0, 0.25, 0), Text.literal(String.format("%.3f", mag)));
                r.arrow(player.getPos(), dir, Blocks.BLUE_CONCRETE.getDefaultState());
            }));
        }

        var collisions = Iterables.concat(collisions(player, futureBox1), collisions(player, futureBox2));
        var it = collisions.iterator();

        if (!it.hasNext()) return;

        onImpact.invoker().onImpact(player, collisions);
    }

    private Iterable<BlockPos> collisions(ServerPlayerEntity player, Box box) {
        // refer to CollisionView::getBlockCollisions
        return () -> new BlockCollisionSpliterator<>(player.getServerWorld(), player, box, false, (pos, voxelShape) -> pos);
    }

    public interface OnImpact {
        void onImpact(ServerPlayerEntity player, Iterable<BlockPos> collisions);
    }
}
