package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class CCBaseManager {

    private final Map<Team, CCBase> bases;
    private final TeamManager teamManager;

    public CCBaseManager(Map<Team, CCBase> bases, TeamManager teamManager) {
        this.bases = bases;
        this.teamManager = teamManager;
    }

    private CCBase requireBase(Team team) {
        return Objects.requireNonNull(bases.get(team), "Base not configured for team " + team.getKey().id());
    }

    public boolean isInAnyBase(double x, double y, double z) {
        return bases.values().stream().anyMatch(base -> base.isInside(x, y, z));
    }

    public boolean isInBase(ServerPlayerEntity player) {
        Team team = teamManager.getTeam(player).orElse(null);
        if (team == null) return false;

        CCBase base = requireBase(team);

        return base.isInside(player.getX(), player.getY(), player.getZ());
    }

    public Optional<CCBase> getBase(Team team) {
        return Optional.ofNullable(bases.get(team));
    }

    public Map<Team, CCBase> getBases() {
        return bases;
    }

    public Optional<Team> getCampfireTeam(BlockPos pos) {
        return bases.entrySet().stream()
                .filter(entry -> entry.getValue().campfirePos().equals(pos))
                .map(Map.Entry::getKey)
                .findAny();
    }

    public Optional<Team> getEntityTeam(Entity entity) {
        return bases.entrySet().stream()
                .filter(entry -> entry.getValue().isEntity(entity))
                .map(Map.Entry::getKey)
                .findAny();
    }

    public void openDoors(ServerWorld world) {
        var opts = EnumSet.of(FORCE_STATE, SKIP_DROPS, SKIP_NEIGHBOUR_UPDATE);

        for (CCBase base : bases.values()) {
            BlockStructure struct = base.doorSchematic();
            BlockPos pos = base.doorPos();

            if (pos == null || struct == null) continue;

            StructureWriter.placeStructure(struct, world, pos, Matrix3i.IDENTITY, opts);
        }
    }
}
