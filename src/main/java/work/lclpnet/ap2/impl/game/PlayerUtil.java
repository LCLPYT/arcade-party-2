package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.combatctl.api.CombatControl;
import work.lclpnet.combatctl.api.CombatStyle;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.lobby.util.PlayerReset;

import java.util.Objects;
import java.util.Set;

public class PlayerUtil {

    public static final GameMode INITIAL_GAMEMODE = GameMode.ADVENTURE;
    private final MinecraftServer server;
    private final PlayerManager playerManager;
    private final CombatControl combatControl;
    private final Set<ApEffect> effects = new ObjectOpenHashSet<>(1);
    private GameMode defaultGameMode = INITIAL_GAMEMODE;
    private CombatStyle defaultCombatStyle = CombatStyle.MODERN;
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

    public GameMode getDefaultGameMode() {
        return defaultGameMode;
    }

    public void setDefaultCombatStyle(CombatStyle defaultCombatStyle) {
        this.defaultCombatStyle = defaultCombatStyle;
        combatControl.setStyle(this.defaultCombatStyle);
    }

    public CombatStyle getDefaultCombatStyle() {
        return defaultCombatStyle;
    }

    public void setAllowFlight(boolean allowFlight) {
        this.allowFlight = allowFlight;

        playerManager.forEach(player -> {
            player.getAbilities().allowFlying = allowFlight;
            player.sendAbilitiesUpdate();
        });
    }

    public boolean isAllowFlight() {
        return allowFlight;
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
        setAllowFlight(false);
        effects.clear();
    }

    public enum State {
        DEFAULT,
        SPECTATOR
    }
}
