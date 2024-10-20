package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.mob.*;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class TargetManager {

    private final PursuitClass<?>[] pursuits;
    private final PursuitClass<ZombieEntity> zombiePursuit;
    private final PursuitClass<AbstractSkeletonEntity> skeletonPursuit;
    private final PursuitClass<PhantomEntity> phantomPursuit;
    private final PursuitClass<VindicatorEntity> vindicatorPursuit;
    private final MobDensityManager densityManager;

    public TargetManager(Participants participants, GameMap map, Random random) {
        zombiePursuit = new PursuitClass<>(participants, 20);
        skeletonPursuit = new PursuitClass<>(participants, 10);
        phantomPursuit = new PursuitClass<>(participants, 3);
        vindicatorPursuit = new PursuitClass<>(participants, 5);

        pursuits = new PursuitClass[] {zombiePursuit, skeletonPursuit, phantomPursuit, vindicatorPursuit};

        densityManager = new MobDensityManager(map, random);
    }

    public void addZombie(ZombieEntity zombie) {
        this.addMob(zombie);
        zombiePursuit.addMob(zombie);
    }

    public void addSkeleton(AbstractSkeletonEntity skeleton) {
        this.addMob(skeleton);
        skeletonPursuit.addMob(skeleton);
    }

    public void addPhantom(PhantomEntity phantom) {
        this.addMob(phantom);
        phantomPursuit.addMob(phantom);
    }

    public void addVindicator(VindicatorEntity vindicator) {
        this.addMob(vindicator);
        vindicatorPursuit.addMob(vindicator);
    }

    private void addMob(MobEntity mob) {
        densityManager.startTracking(mob);
    }

    public void removeMob(MobEntity mob) {
        densityManager.stopTracking(mob);

        switch (mob) {
            case ZombieEntity zombie -> zombiePursuit.removeMob(zombie);
            case SkeletonEntity skeleton -> skeletonPursuit.removeMob(skeleton);
            case PhantomEntity phantom -> phantomPursuit.removeMob(phantom);
            case VindicatorEntity vindicator -> vindicatorPursuit.removeMob(vindicator);
            default -> {}
        }
    }

    public void removeParticipant(ServerPlayerEntity player) {
        for (var pursuit : pursuits) {
            pursuit.removeParticipant(player);
        }
    }

    public void update() {
        densityManager.update();

        for (var pursuit : pursuits) {
            pursuit.update();
        }
    }

    public MobDensityManager getDensityManager() {
        return densityManager;
    }
}
