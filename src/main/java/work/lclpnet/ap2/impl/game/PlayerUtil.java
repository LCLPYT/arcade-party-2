package work.lclpnet.ap2.impl.game;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.util.PlayerUtils;

import java.util.Optional;

public class PlayerUtil {

    public static void resetPlayer(ServerPlayerEntity player, Preset preset) {
        player.changeGameMode(preset == Preset.DEFAULT ? GameMode.SURVIVAL : GameMode.SPECTATOR);
        player.clearStatusEffects();
        player.getInventory().clear();
        PlayerUtils.setCursorStack(player, ItemStack.EMPTY);

        player.getHungerManager().setFoodLevel(20);
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0F);
        player.setExperienceLevel(0);
        player.setExperiencePoints(0);
        player.setFireTicks(0);
        player.setOnFire(false);
        VelocityModifier.setVelocity(player, Vec3d.ZERO);

        Optional.ofNullable(player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH))
                .ifPresent(attribute -> attribute.setBaseValue(20));

        player.dismountVehicle();

        PlayerAbilities abilities = player.getAbilities();
        abilities.setFlySpeed(0.05f);
        abilities.setWalkSpeed(0.1f);

        switch (preset) {
            case DEFAULT -> {
                abilities.flying = false;
                abilities.allowFlying = false;
                abilities.invulnerable = false;
            }
            case SPECTATOR -> {
                abilities.flying = true;
                abilities.allowFlying = true;
                abilities.invulnerable = true;
            }
            default -> {

            }
        }
    }

    public enum Preset {
        DEFAULT,
        SPECTATOR
    }

    private PlayerUtil() {}
}
