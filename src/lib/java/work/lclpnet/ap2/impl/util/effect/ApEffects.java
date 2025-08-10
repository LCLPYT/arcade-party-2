package work.lclpnet.ap2.impl.util.effect;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;

import java.util.*;

public class ApEffects {

    private static final Map<Identifier, ApEffect> effects = new HashMap<>();
    public static final ApEffect DARKNESS = new PotionApEffect(StatusEffects.DARKNESS, 0);
    public static final ApEffect NIGHT_VISION = new PotionApEffect(StatusEffects.NIGHT_VISION, 0);

    static {
        register(ApConstants.identifier("darkness"), DARKNESS);
        register(ApConstants.identifier("night_vision"), NIGHT_VISION);
    }

    private static void register(Identifier id, ApEffect effect) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(effect);

        effects.put(id, effect);
    }

    private ApEffects() {}

    @Nullable
    public static ApEffect tryFrom(Identifier id) {
        return effects.get(id);
    }

    public static Set<ApEffect> fromJson(JSONArray array, Logger logger) {
        Set<ApEffect> effects = new HashSet<>(array.length());

        for (Object obj : array) {
            if (!(obj instanceof String str)) {
                logger.warn("Invalid effect entry of type {}", obj.getClass().getSimpleName());
                continue;
            }

            Identifier id = Identifier.of(str);
            ApEffect effect = ApEffects.tryFrom(id);

            if (effect == null) {
                logger.warn("Unknown effect {}", id);
                continue;
            }

            effects.add(effect);
        }

        return effects;
    }
}
