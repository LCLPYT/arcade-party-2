package work.lclpnet.ap2.impl.util.effect;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.base.ArcadeParty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ApEffects {

    private static final Map<Identifier, ApEffect> effects = new HashMap<>();
    public static final ApEffect DARKNESS = new PotionApEffect(StatusEffects.DARKNESS, 0);
    public static final ApEffect NIGHT_VISION = new PotionApEffect(StatusEffects.NIGHT_VISION, 0);

    static {
        register(ArcadeParty.identifier("darkness"), DARKNESS);
        register(ArcadeParty.identifier("night_vision"), NIGHT_VISION);
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
}
