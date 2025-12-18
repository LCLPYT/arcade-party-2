package work.lclpnet.ap2.game.one_in_the_chamber;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.DeathMessages;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.handler.VisualCooldown;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Random;
import java.util.Set;

import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class OneInTheChamberInstance extends FFAGameInstance {

    static final int SCORE_LIMIT = 15;
    static final double RESPAWN_SPACING = 20;
    private final IntScoreDataContainer<ServerPlayer, PlayerRef> data = new IntScoreDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final OneInTheChamberSpawns respawn = new OneInTheChamberSpawns(gameHandle, random);
    private final SimpleMovementBlocker movementBlocker;
    private final VisualCooldown respawnCooldown;

    public OneInTheChamberInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementBlocker = new SimpleMovementBlocker(gameHandle.getRootScheduler());
        movementBlocker.setModifySpeedAttribute(false);

        respawnCooldown = new VisualCooldown(gameHandle.getScheduler());

        useOldCombat();
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        ServerLevel world = getWorld();

        commons().gameRuleBuilder()
                .set(GameRules.ENTITY_DROPS, false)
                .set(GameRules.NATURAL_HEALTH_REGENERATION, false)
                .set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)
                .set(GameRules.FALL_DAMAGE, false);

        JSONArray spawnsJson = getMap().requireProperty("random-spawns");
        respawn.loadSpawnPoints(spawnsJson);

        HookRegistrar hooks = gameHandle.getHooks();
        movementBlocker.init(hooks);
        respawnCooldown.init(hooks);

        useTaskDisplay();

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        Objective objective = scoreboardManager.createObjective("kills", ObjectiveCriteria.DUMMY,
                Component.literal("Kills").withStyle(YELLOW, BOLD), ObjectiveCriteria.RenderType.INTEGER);

        useScoreboardStatsSync(data, objective);

        scoreboardManager.setDisplay(DisplaySlot.SIDEBAR, objective);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            BlockPos pos = respawn.getRandomSpawn();

            player.teleportTo(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);

            movementBlocker.disableMovement(player);
        }

        hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, event
                -> !event.player().canUseGameMasterBlocks());

        hooks.registerHook(ProjectileHooks.HIT_BLOCK,(projectile, hit)
                -> projectile.discard());

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, this::onDamage);

        hooks.registerHook(SpectatePlayerCallback.HOOK, (spectator, target) -> gameHandle.getParticipants().isParticipating(spectator));

        TaskScheduler scheduler = gameHandle.getScheduler();

        respawnCooldown.setOnCooldownOver(player -> {
            BlockPos randomSpawn = respawn.getRandomSpawn();

            player.teleportTo(world, randomSpawn.getX() + 0.5, randomSpawn.getY(), randomSpawn.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
            giveCrossbowToPlayer(player);

            player.getAbilities().setFlyingSpeed(0);
            player.onUpdateAbilities();

            // delay game mode change one tick to prevent other players from seeing the teleport
            scheduler.immediate(() -> {
                player.getAbilities().setFlyingSpeed(0.05f);
                player.onUpdateAbilities();

                player.setGameMode(gameHandle.getPlayerUtil().getDefaultGameMode());
            });
        });
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                    -> entity instanceof ServerPlayer &&
                    (damageSource.is(DamageTypes.ARROW) || damageSource.is(DamageTypes.PLAYER_ATTACK)));

            config.allow(ProtectionTypes.MOUNT);
        });

        for (ServerPlayer player : gameHandle.getParticipants()) {
            giveCrossbowToPlayer(player);
            giveSwordToPlayer(player);
            movementBlocker.enableMovement(player);
        }
    }

    private void killPlayer(ServerPlayer player, @Nullable ServerPlayer killer, boolean shot) {
        DeathMessages deathMessages = gameHandle.getDeathMessages();

        TranslatedText text;

        if (killer != null) {
            text = shot ? deathMessages.shotBy(player, killer) : deathMessages.killedBy(player, killer);
        } else {
            text = deathMessages.eliminated(player);
        }

        text.formatted(GRAY).sendTo(PlayerLookup.all(gameHandle.getServer()));

        getWorld().playSound(null, player.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 0.8f, 0.8f);

        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(20);

        respawnCooldown.setCooldown(player, 50);
    }

    private void giveCrossbowToPlayer(ServerPlayer player) {
        ItemStack stack = unbreakable(new ItemStack(Items.CROSSBOW));

        stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));

        stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(stack)
                .withStyle(style -> style.withItalic(false).applyFormat(GOLD)));

        Inventory inventory = player.getInventory();
        inventory.setItem(1, stack);
    }

    private void giveSwordToPlayer(ServerPlayer player) {
        ItemStack stack = unbreakable(new ItemStack(Items.STONE_SWORD));

        stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(stack)
                .withStyle(style -> style.withItalic(false).applyFormat(GOLD)));

        Inventory inventory = player.getInventory();
        inventory.setItem(0, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 0);
    }

    private boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player) || winManager.isGameOver()) return false;

        if (source.getDirectEntity() instanceof Projectile projectile) {
            onProjectileDamage(player, projectile);
            return false;
        }

        if (player.hurtTime > 0) return false;

        if ((player.getHealth() - amount) <= 0) {
            onLethalDamage(source, player);
            return false;
        }

        return true;
    }

    private void onLethalDamage(DamageSource source, ServerPlayer player) {
        if (source.getEntity() instanceof ServerPlayer attacker && player != attacker) {
            killPlayer(player, attacker, false);
            onKillGained(attacker);
        } else {
            killPlayer(player, null, false);
        }
    }

    private void onProjectileDamage(ServerPlayer player, Projectile projectile) {
        projectile.discard();

        if (!(projectile.getOwner() instanceof ServerPlayer owner)) return;

        if (owner == player) {
            giveCrossbowToPlayer(owner);
            return;
        }

        killPlayer(player, owner, true);

        onKillGained(owner);
    }

    private void onKillGained(ServerPlayer killer) {
        killer.displayClientMessage(Component.literal("+1 ").append(TextUtil.getVanillaName(Items.ARROW))
                .withStyle(GOLD), true);

        ServerPlayerAccess.playSoundToPlayer(killer, SoundEvents.CROSSBOW_QUICK_CHARGE_3.value(), SoundSource.PLAYERS, 1f, 1f);

        giveCrossbowToPlayer(killer);
        killer.setHealth(20);
        data.addScore(killer, 1);

        int newScore = data.getScore(killer);

        if (newScore == SCORE_LIMIT) {
            winManager.complete();
        }

        ServerPlayerAccess.playSoundToPlayer(killer, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8f, 0.8f);
    }
}
