package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class GbManager {

    private final ServerWorld world;
    private final GameMap map;
    private final Random random;
    private final Map<UUID, GbAnchor> anchors = new HashMap<>();
    private Vec3d circleCenter = null;

    public GbManager(ServerWorld world, GameMap map, Random random) {
        this.world = world;
        this.map = map;
        this.random = random;
    }

    public void setupAnchors(Participants participants) {
        BlockPos center = MapUtil.readBlockPos(map.requireProperty("circle-center"));
        circleCenter = Vec3d.ofBottomCenter(center);

        int pieces = participants.count();
        double radius = CircleStructureGenerator.calculateRadiusExact(pieces, 2);
        double angleStep = Math.PI * 2 / pieces;
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        BlockState state = Blocks.RESPAWN_ANCHOR.getDefaultState();

        int i = 0;

        for (ServerPlayerEntity player : participants) {
            double angle = angleStep * i++;
            Vec3d pos = new Vec3d(
                    cx + Math.sin(angle) * radius,
                    cy,
                    cz + Math.cos(angle) * radius);

            var display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            display.setPosition(pos);
            DisplayEntityAccess.setBlockState(display, state);

            world.spawnEntity(display);

            anchors.put(player.getUuid(), new GbAnchor(pos, display));
        }
    }

    public void teleportPlayers(Participants participants) {
        for (ServerPlayerEntity player : participants) {
            teleport(player);
        }
    }

    private void teleport(ServerPlayerEntity player) {
        GbAnchor anchor = anchors.get(player.getUuid());

        if (anchor == null) return;

        Vec3d center = anchor.pos().add(0.5, 0, 0.5);
        Vec3d dir = center.subtract(circleCenter).normalize();

        if (dir.lengthSquared() < 1e-4) {
            dir = switch (random.nextInt(4)) {
                case 0 -> new Vec3d(1, 0, 0);
                case 1 -> new Vec3d(0, 0, 1);
                case 2 -> new Vec3d(-1, 0, 0);
                default -> new Vec3d(0, 0, -1);
            };
        }

        // find intersection point of cube with r=1 at center with direction vector
        double dx = dir.getX(), dz = dir.getZ();

        double tx = Math.abs(dx) > 1e-4 ? 1 / Math.abs(dx) : Double.POSITIVE_INFINITY;
        double tz = Math.abs(dz) > 1e-4 ? 1 / Math.abs(dz) : Double.POSITIVE_INFINITY;

        Vec3d pos = center.add(dir.multiply(Math.min(tx, tz)));

        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));

        player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), yaw, 0);
    }
}
