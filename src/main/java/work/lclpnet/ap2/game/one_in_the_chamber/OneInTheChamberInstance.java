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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.util.Cooldown;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Random;

import static net.minecraft.util.Formatting.GRAY;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class OneInTheChamberInstance extends DefaultGameInstance {

    private static final int SCORE_LIMIT = 20;
    static final double RESPAWN_SPACING = 20;
    private final ScoreDataContainer data = new ScoreDataContainer();
    private final Random random = new Random();
    private final OneInTheChamberSpawns respawn = new OneInTheChamberSpawns(gameHandle, random);
    private final SimpleMovementBlocker movementBlocker;
    private final Cooldown respawnCooldown;

    public OneInTheChamberInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        TaskScheduler scheduler = gameHandle.getScheduler();

        movementBlocker = new SimpleMovementBlocker(scheduler);
        movementBlocker.setUseStatusEffects(false);

        respawnCooldown = new Cooldown(scheduler);
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

        JSONArray spawnsJson = getMap().requireProperty("random-spawns");
        respawn.loadSpawnPoints(spawnsJson);

        HookRegistrar hooks = gameHandle.getHookRegistrar();
        movementBlocker.init(hooks);
        respawnCooldown.init(hooks);

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

    private void killPlayer(ServerPlayerEntity player) {
        TranslationService translations = gameHandle.getTranslations();

        translations.translateText("ap2.game.eliminated", styled(player.getEntityName(), YELLOW))
                .formatted(GRAY)
                .sendTo(PlayerLookup.all(gameHandle.getServer()));

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
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

        ItemStackUtil.setUnbreakable(stack, true);

        PlayerInventory inventory = player.getInventory();
        inventory.setStack(1, stack);
    }

    private void giveSwordToPlayer(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);

        stack.setCustomName(TextUtil.getVanillaName(stack)
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

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
        killPlayer(player);

        if (source.getAttacker() instanceof ServerPlayerEntity attacker && player != attacker) {
            onKillGained(attacker);
        }
    }

    private void onProjectileDamage(ServerPlayerEntity player, ProjectileEntity projectile) {
        projectile.discard();

        if (!(projectile.getOwner() instanceof ServerPlayerEntity owner)) return;

        if (owner == player) {
            giveCrossbowToPlayer(owner);
            return;
        }

        killPlayer(player);

        onKillGained(owner);
    }

    private void onKillGained(ServerPlayerEntity killer) {
        giveCrossbowToPlayer(killer);
        killer.setHealth(20);
        data.addScore(killer, 1);

        if (data.getScore(killer) == SCORE_LIMIT) {
            win(killer);
        }

        killer.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.8f, 0.8f);
    }
}
