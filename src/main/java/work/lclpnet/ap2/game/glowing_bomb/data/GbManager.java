package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.function.Consumer;

public class GbManager {

    private final ServerWorld world;
    private final GameMap map;
    private final Random random;
    private final Participants participants;
    private final Consumer<GbAnchor> onFull;
    private final List<UUID> orderedPlayers = new ArrayList<>();
    private final Map<UUID, GbAnchor> anchors = new HashMap<>();
    private Vec3d circleCenter = null;
    private UUID bombHolder = null;
    private int playerIndex = -1;

    public GbManager(ServerWorld world, GameMap map, Random random, Participants participants, Consumer<GbAnchor> onFull) {
        this.world = world;
        this.map = map;
        this.random = random;
        this.participants = participants;
        this.onFull = onFull;
    }

    public void setupAnchors() {
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

            UUID uuid = player.getUuid();
            anchors.put(uuid, new GbAnchor(uuid, pos, display));
            orderedPlayers.add(uuid);
        }
    }

    public void teleportPlayers() {
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

    public boolean assignBomb() {
        if (orderedPlayers.isEmpty()) return false;

        playerIndex = random.nextInt(orderedPlayers.size());

        var holder = participants.getParticipant(orderedPlayers.get(playerIndex));

        if (holder.isEmpty()) return false;

        bombHolder = holder.get().getUuid();

        return true;
    }

    @Nullable
    public Vec3d bombLocation() {
        if (bombHolder == null || !participants.isParticipating(bombHolder)) return null;

        GbAnchor anchor = anchors.get(bombHolder);

        if (anchor == null) return null;

        return anchor.pos().add(0.5, 1.5, 0.5);
    }

    @Nullable
    public GbAnchor bombAnchor() {
        if (bombHolder == null || !participants.isParticipating(bombHolder)) return null;

        return anchors.get(bombHolder);
    }

    public Optional<ServerPlayerEntity> bombHolder() {
        return Optional.ofNullable(bombHolder).flatMap(participants::getParticipant);
    }

    public void addCharge(GbAnchor anchor) {
        Vec3d pos = anchor.pos();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;

        int charges = anchor.charges();

        if (charges >= 4) {
            // already full
            world.playSound(null, x, y, z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.5f, 1.5f);
            return;
        }

        anchor.setCharges(charges + 1);

        world.spawnParticles(ParticleTypes.WITCH, x, y, z, 30, 0.1, 0.1, 0.1, 0.5);

        float pitch = charges < 3 ? 1.0f : 1.1f;
        world.playSound(null, x, y, z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1, pitch);

        if (charges == 3) {
            onFull.accept(anchor);
        }
    }

    public void removeAnchor(GbAnchor anchor) {
        UUID uuid = anchor.owner();
        orderedPlayers.remove(uuid);
        anchors.remove(uuid);
        anchor.discard();
    }

    public void removeAnchorOf(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        orderedPlayers.remove(uuid);
        GbAnchor anchor = anchors.remove(uuid);

        if (anchor != null) {
            anchor.discard();
        }
    }

    public boolean hasBomb(ServerPlayerEntity player) {
        return player.getUuid().equals(bombHolder);
    }

    @Nullable
    public ServerPlayerEntity nextBombHolder() {
        if (orderedPlayers.isEmpty()) return null;

        int nextIndex = (playerIndex + 1) % orderedPlayers.size();
        UUID uuid = orderedPlayers.get(nextIndex);

        var holder = participants.getParticipant(uuid);

        if (holder.isEmpty()) return null;

        bombHolder = uuid;
        playerIndex = nextIndex;

        return holder.get();
    }
}
