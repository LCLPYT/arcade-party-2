package work.lclpnet.ap2.impl.util.world;

import net.minecraft.network.packet.s2c.play.WorldBorderInitializeS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldBorderWarningBlocksChangedS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.border.WorldBorder;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.kibu.access.network.packet.WorldBorderWarningBlocksChangedS2CPacketAccess;

public class WorldBorderUtil {

    private WorldBorderUtil() {}

    public static void setWarning(ServerPlayerEntity player) {
        setWarningBlocks(player, Integer.MAX_VALUE);
    }

    public static void setWarningBlocks(ServerPlayerEntity player, int warningBlocks) {
        var packet = WorldBorderWarningBlocksChangedS2CPacketAccess.withWarningBlocks(
                new WorldBorderWarningBlocksChangedS2CPacket(player.getServerWorld().getWorldBorder()),
                warningBlocks);

        player.networkHandler.sendPacket(packet);
    }

    public static void resetWarningBlocks(ServerPlayerEntity player) {
        var packet = new WorldBorderWarningBlocksChangedS2CPacket(player.getServerWorld().getWorldBorder());

        player.networkHandler.sendPacket(packet);
    }

    public static void init(ServerPlayerEntity player, WorldBorder border) {
        player.networkHandler.sendPacket(new WorldBorderInitializeS2CPacket(border));
    }

    public static @NotNull WorldBorder createBorder(double centerX, double centerZ, double size) {
        WorldBorder border = new WorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        return border;
    }
}
