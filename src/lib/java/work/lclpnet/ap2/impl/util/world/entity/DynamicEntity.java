package work.lclpnet.ap2.impl.util.world.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * A dynamic entity is an entity at a shared position, that can be different for individual players.
 * E.g. a text display may exist at a certain position, but it shows a different text for each player.
 */
public interface DynamicEntity {

    Vec3d getPosition();

    /**
     * Return the entity that should be shown for a given player.
     * @param player The player
     * @return The entity, or null if the entity shouldn't exist for the player.
     */
    @Nullable Entity getEntity(ServerPlayerEntity player);

    void cleanup(ServerPlayerEntity player);
}
