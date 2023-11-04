package work.lclpnet.ap2.impl.util.effect;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.base.ArcadeParty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ApEffects {

    private static final Map<Identifier, ApEffect> effects = new HashMap<>();
    public static final ApEffect DARKNESS = register(ArcadeParty.identifier("darkness"), new ApEffect() {
        @Override
        public void apply(ServerPlayerEntity player) {
            var effect = new StatusEffectInstance(StatusEffects.DARKNESS, -1, 0, false, false, false);
            player.addStatusEffect(effect);
        }

        @Override
        public void remove(ServerPlayerEntity player) {
            player.removeStatusEffect(StatusEffects.DARKNESS);
        }
    });

    private static ApEffect register(Identifier id, ApEffect effect) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(effect);

        effects.put(id, effect);

        return effect;
    }

    private ApEffects() {}

    @Nullable
    public static ApEffect tryFrom(Identifier id) {
        return effects.get(id);
    }
}
