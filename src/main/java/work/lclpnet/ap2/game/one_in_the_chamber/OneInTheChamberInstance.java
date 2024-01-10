package work.lclpnet.ap2.game.one_in_the_chamber;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.util.Cooldown;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Random;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class OneInTheChamberInstance extends DefaultGameInstance {

    static final int SCORE_LIMIT = 15;
    static final double RESPAWN_SPACING = 20;
    private final ScoreDataContainer data = new ScoreDataContainer();
    private final Random random = new Random();
    private final OneInTheChamberSpawns respawn = new OneInTheChamberSpawns(gameHandle, random);
    private final SimpleMovementBlocker movementBlocker;
    private final Cooldown respawnCooldown;

    public OneInTheChamberInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementBlocker = new SimpleMovementBlocker(gameHandle.getScheduler());
        movementBlocker.setUseStatusEffects(false);

        respawnCooldown = new Cooldown(gameHandle.getGameScheduler());

        useOldCombat();
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void prepare() {
        GameRules gameRules = getWorld().getGameRules();
        gameRules.get(GameRules.DO_ENTITY_DROPS).set(false, null);
        gameRules.get(GameRules.NATURAL_REGENERATION).set(false, null);
        gameRules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, null);

        JSONArray spawnsJson = getMap().requireProperty("random-spawns");
        respawn.loadSpawnPoints(spawnsJson);

        HookRegistrar hooks = gameHandle.getHookRegistrar();
        movementBlocker.init(hooks);
        respawnCooldown.init(hooks);

        useTaskDisplay();

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        ScoreboardObjective objective = scoreboardManager.createObjective("kills", ScoreboardCriterion.DUMMY,
                Text.literal("Kills").formatted(YELLOW, BOLD), ScoreboardCriterion.RenderType.INTEGER);

        useScoreboardStatsSync(objective);

        scoreboardManager.setDisplay(Scoreboard.SIDEBAR_DISPLAY_SLOT_ID, objective);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            BlockPos pos = respawn.getRandomSpawn();

            player.teleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            movementBlocker.disableMovement(player);
        }

        hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, event
                -> !event.player().isCreativeLevelTwoOp());

        hooks.registerHook(ProjectileHooks.HIT_BLOCK,(projectile, hit)
                -> projectile.discard());

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, this::onDamage);

        respawnCooldown.setOnCooldownOver(player -> {
            BlockPos randomSpawn = respawn.getRandomSpawn();

            player.teleport(randomSpawn.getX() + 0.5, randomSpawn.getY(), randomSpawn.getZ() + 0.5);
            giveCrossbowToPlayer(player);

            player.changeGameMode(gameHandle.getPlayerUtil().getDefaultGameMode());
        });
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                -> damageSource.isOf(DamageTypes.ARROW) || damageSource.isOf(DamageTypes.PLAYER_ATTACK)));

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            giveCrossbowToPlayer(player);
            giveSwordToPlayer(player);
            movementBlocker.enableMovement(player);
        }
    }

    private void killPlayer(ServerPlayerEntity player, @Nullable ServerPlayerEntity killer, boolean shot) {
        TranslationService translations = gameHandle.getTranslations();

        FormatWrapper victim = styled(player.getEntityName(), YELLOW);
        TranslatedText text;

        if (killer != null) {
            String key = shot ? "ap2.pvp.shot_by" : "ap2.pvp.killed_by";
            text = translations.translateText(key, victim, styled(killer.getEntityName(), YELLOW));
        } else {
            text = translations.translateText("ap2.game.eliminated", victim);
        }

        text.formatted(GRAY).sendTo(PlayerLookup.all(gameHandle.getServer()));

        getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 0.8f, 0.8f);

        player.changeGameMode(GameMode.SPECTATOR);
        player.setHealth(20);

        respawnCooldown.setCooldown(player, 50);
    }

    private void giveCrossbowToPlayer(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.CROSSBOW);

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtList nbtList = new NbtList();
        NbtCompound arrow = new NbtCompound();

        arrow.putInt("Count", 1);
        arrow.putString("id", Registries.ITEM.getId(Items.ARROW).toString());
        nbtList.add(arrow);

        nbt.putBoolean("Charged", true);
        nbt.put("ChargedProjectiles", nbtList);

        stack.setCustomName(TextUtil.getVanillaName(stack)
                .styled(style -> style.withItalic(false).withFormatting(GOLD)));

        ItemStackUtil.setUnbreakable(stack, true);

        PlayerInventory inventory = player.getInventory();
        inventory.setStack(1, stack);
    }

    private void giveSwordToPlayer(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.STONE_SWORD);

        stack.setCustomName(TextUtil.getVanillaName(stack)
                .styled(style -> style.withItalic(false).withFormatting(GOLD)));

        ItemStackUtil.setUnbreakable(stack, true);

        PlayerInventory inventory = player.getInventory();
        inventory.setStack(0, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 0);
    }

    private boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) return false;

        if (source.getSource() instanceof ProjectileEntity projectile) {
            onProjectileDamage(player, projectile);
            return false;
        }

        if ((player.getHealth() - amount) <= 0) {
            onLethalDamage(source, player);
            return false;
        }

        return true;
    }

    private void onLethalDamage(DamageSource source, ServerPlayerEntity player) {
        if (source.getAttacker() instanceof ServerPlayerEntity attacker && player != attacker) {
            killPlayer(player, attacker, false);
            onKillGained(attacker);
        } else {
            killPlayer(player, null, false);
        }
    }

    private void onProjectileDamage(ServerPlayerEntity player, ProjectileEntity projectile) {
        projectile.discard();

        if (!(projectile.getOwner() instanceof ServerPlayerEntity owner)) return;

        if (owner == player) {
            giveCrossbowToPlayer(owner);
            return;
        }

        killPlayer(player, owner, true);

        onKillGained(owner);
    }

    private void onKillGained(ServerPlayerEntity killer) {
        killer.sendMessage(Text.literal("+1 ").append(TextUtil.getVanillaName(Items.ARROW))
                .formatted(GOLD), true);

        killer.playSound(SoundEvents.ITEM_CROSSBOW_QUICK_CHARGE_3, SoundCategory.PLAYERS, 1f, 1f);

        giveCrossbowToPlayer(killer);
        killer.setHealth(20);
        data.addScore(killer, 1);

        int newScore = data.getScore(killer);

        if (newScore == SCORE_LIMIT) {
            win(killer);
        }

        killer.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.8f, 0.8f);
    }
}
