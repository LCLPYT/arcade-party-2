package work.lclpnet.ap2.game.glowing_bomb.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.function.Consumer;

public class GbManager {

    private final ServerLevel world;
    private final GameMap map;
    private final Random random;
    private final Participants participants;
    private final Consumer<GbAnchor> onFull;
    private final List<UUID> orderedPlayers = new ArrayList<>();
    private final Map<UUID, GbAnchor> anchors = new HashMap<>();
    private Vec3 circleCenter = null;
    private UUID bombHolder = null;
    private int playerIndex = -1;

    public GbManager(ServerLevel world, GameMap map, Random random, Participants participants, Consumer<GbAnchor> onFull) {
        this.world = world;
        this.map = map;
        this.random = random;
        this.participants = participants;
        this.onFull = onFull;
    }

    public void setupAnchors() {
        BlockPos center = MapUtil.readBlockPos(map.requireProperty("circle-center"));
        circleCenter = Vec3.atBottomCenterOf(center);

        int pieces = participants.count();
        double radius = CircleStructureGenerator.calculateRadiusExact(pieces, 2);
        double angleStep = Math.PI * 2 / pieces;
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        BlockState state = Blocks.RESPAWN_ANCHOR.defaultBlockState();

        int i = 0;

        for (ServerPlayer player : participants) {
            double angle = angleStep * i++;
            Vec3 pos = new Vec3(
                    cx + Math.sin(angle) * radius,
                    cy,
                    cz + Math.cos(angle) * radius);

            var display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, world);
            display.setPos(pos);
            DisplayEntityAccess.setBlockState(display, state);

            world.addFreshEntity(display);

            UUID uuid = player.getUUID();
            anchors.put(uuid, new GbAnchor(uuid, pos, display));
            orderedPlayers.add(uuid);
        }
    }

    public void teleportPlayers() {
        for (ServerPlayer player : participants) {
            teleport(player);
        }
    }

    private void teleport(ServerPlayer player) {
        GbAnchor anchor = anchors.get(player.getUUID());

        if (anchor == null) return;

        Vec3 center = anchor.pos().add(0.5, 0, 0.5);
        Vec3 dir = center.subtract(circleCenter).normalize();

        if (dir.lengthSqr() < 1e-4) {
            dir = switch (random.nextInt(4)) {
                case 0 -> new Vec3(1, 0, 0);
                case 1 -> new Vec3(0, 0, 1);
                case 2 -> new Vec3(-1, 0, 0);
                default -> new Vec3(0, 0, -1);
            };
        }

        // find intersection point of cube with r=1 at center with direction vector
        double dx = dir.x(), dz = dir.z();

        double tx = Math.abs(dx) > 1e-4 ? 1 / Math.abs(dx) : Double.POSITIVE_INFINITY;
        double tz = Math.abs(dz) > 1e-4 ? 1 / Math.abs(dz) : Double.POSITIVE_INFINITY;

        Vec3 pos = center.add(dir.scale(Math.min(tx, tz)));

        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));

        player.teleportTo(world, pos.x(), pos.y(), pos.z(), Set.of(), yaw, 0, true);
    }

    public boolean assignBomb() {
        if (orderedPlayers.isEmpty()) return false;

        playerIndex = random.nextInt(orderedPlayers.size());

        var holder = participants.getParticipant(orderedPlayers.get(playerIndex));

        if (holder.isEmpty()) return false;

        bombHolder = holder.get().getUUID();

        return true;
    }

    @Nullable
    public Vec3 bombLocation() {
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

    public Optional<ServerPlayer> bombHolder() {
        return Optional.ofNullable(bombHolder).flatMap(participants::getParticipant);
    }

    public void addCharge(GbAnchor anchor) {
        Vec3 pos = anchor.pos();
        double x = pos.x() + 0.5, y = pos.y() + 0.5, z = pos.z() + 0.5;

        int charges = anchor.charges();

        if (charges >= 4) {
            // already full
            world.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 1.5f);
            return;
        }

        anchor.setCharges(charges + 1);

        world.sendParticles(ParticleTypes.WITCH, x, y, z, 30, 0.1, 0.1, 0.1, 0.5);

        float pitch = charges < 3 ? 1.0f : 1.1f;
        world.playSound(null, x, y, z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1, pitch);

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

    public void removeAnchorOf(ServerPlayer player) {
        UUID uuid = player.getUUID();
        orderedPlayers.remove(uuid);
        GbAnchor anchor = anchors.remove(uuid);

        if (anchor != null) {
            anchor.discard();
        }
    }

    public boolean hasBomb(ServerPlayer player) {
        return player.getUUID().equals(bombHolder);
    }

    @Nullable
    public ServerPlayer nextBombHolder() {
        if (orderedPlayers.isEmpty()) return null;

        int nextIndex = Math.floorMod(playerIndex - 1,  orderedPlayers.size());
        UUID uuid = orderedPlayers.get(nextIndex);

        var holder = participants.getParticipant(uuid);

        if (holder.isEmpty()) return null;

        bombHolder = uuid;
        playerIndex = nextIndex;

        return holder.get();
    }
}
