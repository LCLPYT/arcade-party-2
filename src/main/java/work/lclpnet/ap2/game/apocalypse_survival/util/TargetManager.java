package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.lobby.game.map.GameMap;

public class TargetManager {

    private final CustomScoreboardManager debug$scoreboardManager;
    private final Team debug$pursuitTeam, debug$roamTeam;
    private final PursuitClass<ZombieEntity> zombiePursuit;
    private final MobDensityManager densityManager;

    public TargetManager(Participants participants, GameMap map,
                         CustomScoreboardManager debug$scoreboardManager, Team debug$pursuitTeam, Team debug$roamTeam) {
        this.debug$scoreboardManager = debug$scoreboardManager;
        this.debug$pursuitTeam = debug$pursuitTeam;
        this.debug$roamTeam = debug$roamTeam;

        zombiePursuit = new PursuitClass<>(participants, 20, this);
        densityManager = new MobDensityManager(map);
    }

    public void addZombie(ZombieEntity zombie) {
        this.addMob(zombie);
        zombiePursuit.addMob(zombie);
    }

    private void addMob(MobEntity mob) {
        densityManager.startTracking(mob);
    }

    public void removeMob(MobEntity mob) {
        densityManager.stopTracking(mob);

        switch (mob) {
            case ZombieEntity zombie -> zombiePursuit.removeMob(zombie);
            default -> {}
        }
    }

    public void removeParticipant(ServerPlayerEntity player) {
        zombiePursuit.removeParticipant(player);
    }

    public void update() {
        densityManager.update();
        zombiePursuit.update();
    }

    public MobDensityManager getDensityManager() {
        return densityManager;
    }

    public Team debug$getPursuitTeam() {
        return debug$pursuitTeam;
    }

    public Team debug$getRoamTeam() {
        return debug$roamTeam;
    }

    public CustomScoreboardManager debug$getScoreboardManager() {
        return debug$scoreboardManager;
    }
}
