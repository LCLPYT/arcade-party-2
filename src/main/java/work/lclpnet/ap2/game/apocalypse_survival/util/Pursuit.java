package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class Pursuit<T extends MobEntity> {

    private final Supplier<@Nullable LivingEntity> targetGetter;
    private final int capacity;
    private final Set<T> pursuers;
    private final List<T> byDistance;
    private boolean needsUpdate = false;

    public Pursuit(Supplier<@Nullable LivingEntity> targetGetter, int capacity) {
        this.targetGetter = targetGetter;
        this.capacity = capacity;
        this.pursuers = new HashSet<>(capacity);
        this.byDistance = new ArrayList<>(capacity);
    }

    public boolean hasCapacity() {
        return pursuers.size() < capacity;
    }

    public boolean addPursuer(T mob) {
        if (!hasCapacity() || !pursuers.add(mob)) {
            return false;
        }

        setNeedsUpdate();

        return true;
    }

    public void removePursuer(T mob) {
        if (pursuers.remove(mob)) {
            setNeedsUpdate();
        }
    }

    @Nullable
    public T getMostDistantPursuer() {
        if (needsUpdate) {
            updateDistances();
        }

        if (byDistance.isEmpty()) {
            return null;
        }

        return byDistance.getLast();
    }

    private void updateDistances() {
        byDistance.clear();

        LivingEntity target = targetGetter.get();

        if (target == null) return;

        byDistance.addAll(pursuers);
        byDistance.sort(Comparator.comparingDouble(target::squaredDistanceTo));
    }

    public void setNeedsUpdate() {
        needsUpdate = true;
    }
}
