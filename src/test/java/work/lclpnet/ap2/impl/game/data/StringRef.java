package work.lclpnet.ap2.impl.game.data;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.data.SubjectRef;

public record StringRef(String name) implements SubjectRef {

    @Override
    public Text getNameFor(ServerPlayerEntity player) {
        return Text.literal(name);
    }
}
