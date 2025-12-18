package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.combatctl.api.CombatControl;
import work.lclpnet.combatctl.api.CombatStyle;
import work.lclpnet.combatctl.impl.CombatStyles;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.util.PlayerReset;

import java.util.*;

import static java.lang.Math.*;

public class PlayerUtil {

    public static final GameType INITIAL_GAMEMODE = GameType.ADVENTURE;
    private final MinecraftServer server;
    private final PlayerManager playerManager;
    private final CombatControl combatControl;
    private final Set<ApEffect> effects = new ObjectOpenHashSet<>(1);
    private final Map<UUID, State> stateOverrides = new HashMap<>();
    @Getter
    private GameType defaultGameMode = INITIAL_GAMEMODE;
    @Getter
    private CombatStyle defaultCombatStyle = CombatStyles.MODERN;
    @Getter
    private boolean allowFlight = false;

    public PlayerUtil(MinecraftServer server, PlayerManager playerManager) {
        this.server = server;
        this.playerManager = playerManager;
        this.combatControl = CombatControl.get(server);
    }

    public void setDefaultGameMode(@NotNull GameType defaultGameMode) {
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
            player.getAbilities().mayfly = allowFlight;
            player.onUpdateAbilities();
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

    public void setStateOverride(ServerPlayer player, @Nullable State state) {
        if (state == null) {
            stateOverrides.remove(player.getUUID());
        } else {
            stateOverrides.put(player.getUUID(), state);
        }
    }

    @NotNull
    public State getState(ServerPlayer player) {
        State override = stateOverrides.get(player.getUUID());

        if (override != null) {
            return override;
        }

        return playerManager.isParticipating(player) ? State.DEFAULT : State.SPECTATOR;
    }

    public void resetPlayer(ServerPlayer player) {
        resetPlayer(player, getState(player));
    }

    public void resetPlayer(ServerPlayer player, State state) {
        player.setGameMode(state == State.DEFAULT ? defaultGameMode : GameType.SPECTATOR);
        player.removeAllEffects();
        player.getInventory().clearContent();
        PlayerUtils.setCursorStack(player, ItemStack.EMPTY);

        player.getFoodData().setFoodLevel(20);
        player.setAbsorptionAmount(0F);
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.setRemainingFireTicks(0);
        player.setSharedFlagOnFire(false);
        player.setArrowCount(0);
        VelocityModifier.setVelocity(player, Vec3.ZERO);

        PlayerReset.resetAttributes(player);

        player.setHealth(player.getMaxHealth());
        player.removeVehicle();

        PlayerReset.resetSpawnPoint(player);

        Abilities abilities = player.getAbilities();
        abilities.setFlyingSpeed(0.05f);
        PlayerReset.modifyWalkSpeed(player, 0.1f, false);

        switch (state) {
            case DEFAULT -> {
                abilities.flying = false;
                abilities.mayfly = allowFlight;
                abilities.invulnerable = false;
            }
            case SPECTATOR -> {
                abilities.flying = true;
                abilities.mayfly = true;
                abilities.invulnerable = true;
            }
            default -> {}
        }

        player.onUpdateAbilities();

        effects.forEach(effect -> effect.apply(player));

        combatControl.setStyle(player, defaultCombatStyle);
    }

    public void resetToDefaults() {
        setDefaultGameMode(PlayerUtil.INITIAL_GAMEMODE);
        setDefaultCombatStyle(CombatStyles.MODERN
                .andThen(player -> player.setDisableOldBobbing(false), global -> {}));

        setAllowFlight(false);
        effects.clear();
        stateOverrides.clear();
    }

    public void updatePlayerListNames() {
        Collection<ServerPlayer> players = PlayerLookup.all(server);

        updatePlayerListNames(players);
    }

    public void updatePlayerListNames(Collection<ServerPlayer> players) {
        var packet = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), players);

        server.getPlayerList().broadcastAll(packet);
    }

    public static int getLoadingDelayTicks(int players) {
        return Ticks.seconds(5) + players * 10;
    }

    public static Vec3 getRelativeHorizontalInputVector(Input input) {
        double x = 0, y = 0, z = 0;

        if (input.forward()) z += 1;
        if (input.backward()) z -= 1;

        if (input.left()) x += 1;
        if (input.right()) x -= 1;

        double lenSq = x * x + y * y + z * z;

        if (abs(lenSq) < 1e-6) {
            return Vec3.ZERO;
        }

        double len = sqrt(lenSq);

        return new Vec3(x / len, y / len, z / len);
    }

    public static Vec3 getHorizontalInputVector(ServerPlayer player) {
        Vec3 relInput = getRelativeHorizontalInputVector(player.getLastClientInput());

        return relInput.yRot((float) toRadians(-player.getYRot()));
    }

    public enum State {
        DEFAULT,
        SPECTATOR
    }
}
