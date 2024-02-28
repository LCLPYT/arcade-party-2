package work.lclpnet.ap2.api.game.data;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public interface SubjectRef {

    /**
     * Translate the name of this subject for a given viewer (a player).
     * @param viewer The viewer (to whom the name is translated for).
     * @return The translated text.
     */
    Text getNameFor(ServerPlayerEntity viewer);
}
