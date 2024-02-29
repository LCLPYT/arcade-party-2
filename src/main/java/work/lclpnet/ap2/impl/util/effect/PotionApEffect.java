package work.lclpnet.ap2.impl.util.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;

public class PotionApEffect implements ApEffect {

    private final StatusEffect effect;
    private final int amplifier;

    public PotionApEffect(StatusEffect effect, int amplifier) {
        this.effect = effect;
        this.amplifier = amplifier;
    }

    @Override
    public void apply(ServerPlayerEntity player) {
        var instance = new StatusEffectInstance(this.effect, -1, this.amplifier, false, false, false);
        player.addStatusEffect(instance);
    }

    @Override
    public void remove(ServerPlayerEntity player) {
        player.removeStatusEffect(this.effect);
    }
}
