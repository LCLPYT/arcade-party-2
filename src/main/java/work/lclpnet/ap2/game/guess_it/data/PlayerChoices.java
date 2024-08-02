package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.translate.Translations;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

public class PlayerChoices {

    private final Translations translations;
    private final Map<UUID, String> choices = new HashMap<>();

    public PlayerChoices(Translations translations) {
        this.translations = translations;
    }

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

    public Optional<Float> getFloat(ServerPlayerEntity player) {
        String c = choices.get(player.getUuid());

        if (c == null) {
            return Optional.empty();
        }

        Locale locale = translations.getLocale(player);
        NumberFormat format = NumberFormat.getInstance(locale);

        try {
            float f = format.parse(c).floatValue();
            return Optional.of(f);
        } catch (ParseException e) {
            return Optional.empty();
        }
    }

    public OptionalInt getOption(ServerPlayerEntity player) {
        String in = choices.get(player.getUuid());

        if (in == null || in.length() != 1) {
            return OptionalInt.empty();
        }

        char c = in.charAt(0);
        int option = c - 'A';

        return OptionalInt.of(option);
    }

    public void clear() {
        choices.clear();
    }
}
