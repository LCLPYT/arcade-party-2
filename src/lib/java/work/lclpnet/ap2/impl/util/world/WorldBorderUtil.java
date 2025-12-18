package work.lclpnet.ap2.impl.util.world;

import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.kibu.access.network.packet.WorldBorderWarningBlocksChangedS2CPacketAccess;

public class WorldBorderUtil {

    private WorldBorderUtil() {}

    public static void setWarning(ServerPlayer player) {
        setWarningBlocks(player, Integer.MAX_VALUE);
    }

    public static void setWarningBlocks(ServerPlayer player, int warningBlocks) {
        var packet = WorldBorderWarningBlocksChangedS2CPacketAccess.withWarningBlocks(
                new ClientboundSetBorderWarningDistancePacket(player.level().getWorldBorder()),
                warningBlocks);

        player.connection.send(packet);
    }

    public static void resetWarningBlocks(ServerPlayer player) {
        var packet = new ClientboundSetBorderWarningDistancePacket(player.level().getWorldBorder());

        player.connection.send(packet);
    }

    public static void init(ServerPlayer player, WorldBorder border) {
        player.connection.send(new ClientboundInitializeBorderPacket(border));
    }

    public static @NotNull WorldBorder createBorder(double centerX, double centerZ, double size) {
        WorldBorder border = new WorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        return border;
    }
}
