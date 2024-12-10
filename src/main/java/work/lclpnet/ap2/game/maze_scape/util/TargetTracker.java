package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class TargetTracker<Pos extends TargetTracker.Key> {

    private final UUID uuid;
    private final ServerWorld world;
    private final Function<ServerPlayerEntity, Pos> positionProvider;
    private final Comparison<MobEntity, Pos> comparison;
    private final List<Pos> targets = new ArrayList<>();
    private @Nullable MobEntity $mob;

    public TargetTracker(MobEntity mob, ServerWorld world, Function<ServerPlayerEntity, Pos> posProvider, Comparison<MobEntity, Pos> posComparison) {
        this.uuid = mob.getUuid();
        this.world = world;
        this.$mob = mob;
        this.positionProvider = posProvider;
        this.comparison = posComparison;
    }

    public synchronized @Nullable MobEntity mob() {
        if ($mob == null || $mob.isRemoved()) {
            Entity entity = world.getEntity(uuid);

            if (!(entity instanceof MobEntity mob)) {
                return null;
            }

            this.$mob = mob;
        }

        return $mob;
    }

    public synchronized void update(Set<? extends ServerPlayerEntity> players) {
        updatePriorities(players);


    }

    public void updatePriorities(Set<? extends ServerPlayerEntity> players) {
        targets.clear();

        MobEntity mob = mob();

        if (mob == null) return;

        var comp = comparison.withRespectTo(mob);

        if (comp == null) return;

        for (ServerPlayerEntity player : players) {
            Pos pos = positionProvider.apply(player);

            if (pos == null) continue;

            targets.add(pos);
        }

        targets.sort(comp);
    }

    public List<Pos> targets() {
        return targets;
    }

    public synchronized @Nullable ServerPlayerEntity target() {
        if (targets.isEmpty()) return null;

        Pos first = targets.getFirst();

        return world.getServer().getPlayerManager().getPlayer(first.uuid());
    }

    public interface Key {
        @NotNull UUID uuid();
    }

    public interface Comparison<Ref, T> {
        @Nullable Comparator<T> withRespectTo(Ref ref);
    }
}
