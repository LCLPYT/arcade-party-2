package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.*;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class TargetManager {

    private final PursuitClass<?>[] pursuits;
    private final PursuitClass<Zombie> zombiePursuit;
    private final PursuitClass<AbstractSkeleton> skeletonPursuit;
    private final PursuitClass<Phantom> phantomPursuit;
    private final PursuitClass<Vindicator> vindicatorPursuit;
    private final MobDensityManager densityManager;

    public TargetManager(Participants participants, GameMap map, Random random) {
        zombiePursuit = new PursuitClass<>(participants, 20);
        skeletonPursuit = new PursuitClass<>(participants, 10);
        phantomPursuit = new PursuitClass<>(participants, 3);
        vindicatorPursuit = new PursuitClass<>(participants, 5);

        pursuits = new PursuitClass[] {zombiePursuit, skeletonPursuit, phantomPursuit, vindicatorPursuit};

        densityManager = new MobDensityManager(map, random);
    }

    public void addZombie(Zombie zombie) {
        this.addMob(zombie);
        zombiePursuit.addMob(zombie);
    }

    public void addSkeleton(AbstractSkeleton skeleton) {
        this.addMob(skeleton);
        skeletonPursuit.addMob(skeleton);
    }

    public void addPhantom(Phantom phantom) {
        this.addMob(phantom);
        phantomPursuit.addMob(phantom);
    }

    public void addVindicator(Vindicator vindicator) {
        this.addMob(vindicator);
        vindicatorPursuit.addMob(vindicator);
    }

    private void addMob(Mob mob) {
        densityManager.startTracking(mob);
    }

    public void removeMob(Mob mob) {
        densityManager.stopTracking(mob);

        switch (mob) {
            case Zombie zombie -> zombiePursuit.removeMob(zombie);
            case Skeleton skeleton -> skeletonPursuit.removeMob(skeleton);
            case Phantom phantom -> phantomPursuit.removeMob(phantom);
            case Vindicator vindicator -> vindicatorPursuit.removeMob(vindicator);
            default -> {}
        }
    }

    public void removeParticipant(ServerPlayer player) {
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
