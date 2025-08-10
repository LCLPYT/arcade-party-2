package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.combatctl.api.CombatControl;
import work.lclpnet.combatctl.api.CombatStyle;
import work.lclpnet.combatctl.impl.CombatStyles;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.util.PlayerReset;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import static java.lang.Math.*;

public class PlayerUtil {

    public static final GameMode INITIAL_GAMEMODE = GameMode.ADVENTURE;
    private final MinecraftServer server;
    private final PlayerManager playerManager;
    private final CombatControl combatControl;
    private final Set<ApEffect> effects = new ObjectOpenHashSet<>(1);
    @Getter
    private GameMode defaultGameMode = INITIAL_GAMEMODE;
    @Getter
    private CombatStyle defaultCombatStyle = CombatStyles.MODERN;
    @Getter
    private boolean allowFlight = false;

    public PlayerUtil(MinecraftServer server, PlayerManager playerManager) {
        this.server = server;
        this.playerManager = playerManager;
        this.combatControl = CombatControl.get(server);
    }

    public void setDefaultGameMode(@NotNull GameMode defaultGameMode) {
        Objects.requireNonNull(defaultGameMode);
        this.defaultGameMode = defaultGameMode;
    }

    public void setDefaultCombatStyle(CombatStyle defaultCombatStyle) {
        this.defaultCombatStyle = defaultCombatStyle;
        combatControl.setStyle(this.defaultCombatStyle);
    }

    public void setAllowFlight(boolean allowFlight) {
        this.allowFlight = allowFlight;

        playerManager.forEach(player -> {
            player.getAbilities().allowFlying = allowFlight;
            player.sendAbilitiesUpdate();
        });
    }

    public void enableEffect(ApEffect effect) {
        Objects.requireNonNull(effect);
        effects.add(effect);

        var players = effect.isGlobal() ? PlayerLookup.all(server) : playerManager;
        players.forEach(effect::apply);
    }

    public void disableEffect(ApEffect effect) {
        Objects.requireNonNull(effect);
        effects.remove(effect);

        var players = effect.isGlobal() ? PlayerLookup.all(server) : playerManager;
        players.forEach(effect::remove);
    }

    @NotNull
    public State getState(ServerPlayerEntity player) {
        return playerManager.isParticipating(player) ? State.DEFAULT : State.SPECTATOR;
    }

    public void resetPlayer(ServerPlayerEntity player) {
        resetPlayer(player, getState(player));
    }

    public void resetPlayer(ServerPlayerEntity player, State state) {
        player.changeGameMode(state == State.DEFAULT ? defaultGameMode : GameMode.SPECTATOR);
        player.clearStatusEffects();
        player.getInventory().clear();
        PlayerUtils.setCursorStack(player, ItemStack.EMPTY);

        player.getHungerManager().setFoodLevel(20);
        player.setAbsorptionAmount(0F);
        player.setExperienceLevel(0);
        player.setExperiencePoints(0);
        player.setFireTicks(0);
        player.setOnFire(false);
        player.setStuckArrowCount(0);
        VelocityModifier.setVelocity(player, Vec3d.ZERO);

        PlayerReset.resetAttributes(player);

        player.setHealth(player.getMaxHealth());
        player.dismountVehicle();

        PlayerReset.resetSpawnPoint(player);

        PlayerAbilities abilities = player.getAbilities();
        abilities.setFlySpeed(0.05f);
        PlayerReset.modifyWalkSpeed(player, 0.1f, false);

        switch (state) {
            case DEFAULT -> {
                abilities.flying = false;
                abilities.allowFlying = allowFlight;
                abilities.invulnerable = false;
            }
            case SPECTATOR -> {
                abilities.flying = true;
                abilities.allowFlying = true;
                abilities.invulnerable = true;
            }
            default -> {}
        }

        player.sendAbilitiesUpdate();

        effects.forEach(effect -> effect.apply(player));

        combatControl.setStyle(player, defaultCombatStyle);
    }

    public void resetToDefaults() {
        setDefaultGameMode(PlayerUtil.INITIAL_GAMEMODE);
        setDefaultCombatStyle(CombatStyles.MODERN
                .andThen(player -> player.setDisableOldBobbing(false), global -> {}));

        setAllowFlight(false);
        effects.clear();
    }

    public void updatePlayerListNames() {
        Collection<ServerPlayerEntity> players = PlayerLookup.all(server);

        updatePlayerListNames(players);
    }

    public void updatePlayerListNames(Collection<ServerPlayerEntity> players) {
        var packet = new PlayerListS2CPacket(EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME), players);

        server.getPlayerManager().sendToAll(packet);
    }

    public static int getLoadingDelayTicks(int players) {
        return Ticks.seconds(5) + players * 10;
    }

    public static Vec3d getRelativeHorizontalInputVector(PlayerInput input) {
        double x = 0, y = 0, z = 0;

        if (input.forward()) z += 1;
        if (input.backward()) z -= 1;

        if (input.left()) x += 1;
        if (input.right()) x -= 1;

        double lenSq = x * x + y * y + z * z;

        if (abs(lenSq) < 1e-6) {
            return Vec3d.ZERO;
        }

        double len = sqrt(lenSq);

        return new Vec3d(x / len, y / len, z / len);
    }

    public static Vec3d getHorizontalInputVector(ServerPlayerEntity player) {
        Vec3d relInput = getRelativeHorizontalInputVector(player.getPlayerInput());

        return relInput.rotateY((float) toRadians(-player.getYaw()));
    }

    public enum State {
        DEFAULT,
        SPECTATOR
    }
}
