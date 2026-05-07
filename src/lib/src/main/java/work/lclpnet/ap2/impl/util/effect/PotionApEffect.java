package work.lclpnet.ap2.impl.util.effect;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public record PotionApEffect(Holder<MobEffect> effect, int amplifier) implements ApEffect {

    @Override
    public void apply(ServerPlayer player) {
        var instance = new MobEffectInstance(this.effect, -1, this.amplifier, false, false, false);
        player.addEffect(instance);
    }

    @Override
    public void remove(ServerPlayer player) {
        player.removeEffect(this.effect);
    }
}
