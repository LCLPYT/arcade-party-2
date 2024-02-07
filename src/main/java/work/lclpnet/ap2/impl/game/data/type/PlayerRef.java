package work.lclpnet.ap2.impl.game.data.type;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.data.SubjectRef;

import java.util.Objects;
import java.util.UUID;

public record PlayerRef(UUID uuid, String name) implements SubjectRef {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerRef playerRef = (PlayerRef) o;
        return Objects.equals(uuid, playerRef.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public Text getName() {
        return Text.literal(name);
    }

    public static PlayerRef create(ServerPlayerEntity player) {
        return new PlayerRef(player.getUuid(), player.getNameForScoreboard());
    }
}
