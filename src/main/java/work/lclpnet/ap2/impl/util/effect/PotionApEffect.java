package work.lclpnet.ap2.impl.util.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

public record PotionApEffect(RegistryEntry<StatusEffect> effect, int amplifier) implements ApEffect {

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
