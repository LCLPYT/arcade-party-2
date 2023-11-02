package work.lclpnet.ap2.impl;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.player.PlayerToggleFlightCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

public class DoubleJumpHandler {

    private final PlayerUtil playerUtil;
    private boolean enabled = false;

    public DoubleJumpHandler(PlayerUtil playerUtil) {
        this.playerUtil = playerUtil;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;

        this.enabled = enabled;

        playerUtil.setAllowFlight(enabled);
    }

    public void init(HookRegistrar hookRegistrar) {
        hookRegistrar.registerHook(PlayerToggleFlightCallback.HOOK, (player, fly) -> {
            if (!fly) return false;

            VelocityModifier.setVelocity(player, player.getRotationVector().multiply(1.3));

            ServerWorld serverWorld = player.getServerWorld();

            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            serverWorld.spawnParticles(ParticleTypes.CLOUD, x, y, z, 10, 0.1, 0.1, 0.1, 0.1);
            serverWorld.playSound(null, x, y, z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1, 1);

            return true;
        });

        setEnabled(true);
    }
}
