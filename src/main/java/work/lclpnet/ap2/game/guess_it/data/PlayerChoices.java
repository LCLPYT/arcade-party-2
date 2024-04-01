package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public class PlayerChoices {

    private final Map<UUID, String> choices = new HashMap<>();

    public void set(ServerPlayerEntity player, String choice) {
        choices.put(player.getUuid(), choice);
    }

    public Optional<String> get(ServerPlayerEntity player) {
        String c = choices.get(player.getUuid());

        return Optional.ofNullable(c);
    }

    public OptionalInt getInt(ServerPlayerEntity player) {
        String c = choices.get(player.getUuid());

        if (c == null) {
            return OptionalInt.empty();
        }

        try {
            int i = Integer.parseInt(c, 10);
            return OptionalInt.of(i);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public void clear() {
        choices.clear();
    }
}
