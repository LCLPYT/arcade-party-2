package work.lclpnet.ap2.game.one_in_the_chamber;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import static net.minecraft.util.Formatting.GRAY;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class OneInTheChamberInstance extends DefaultGameInstance {

    int scoreLimit = 20;
    private final ScoreDataContainer data = new ScoreDataContainer();
    private final ArrayList<BlockPos> spawnPoints = new ArrayList<>();
    private final ArrayList<ArrayList<BlockPos>> grid = new ArrayList<>();
    private final OneInTheChamberRespawn respawn = new OneInTheChamberRespawn();
    private final SimpleMovementBlocker movementBlocker;
    public OneInTheChamberInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        movementBlocker = new SimpleMovementBlocker(gameHandle.getScheduler());
        movementBlocker.setUseStatusEffects(false);
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void prepare() {

        movementBlocker.init(gameHandle.getHookRegistrar());

        final GameMap map = getMap();

        loadSpawnPoints(map, spawnPoints);

        ArrayList<BlockPos> initialSpawnPoints = new ArrayList<>(spawnPoints);

        BlockPos randomSpawn;

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {

            Collections.shuffle(initialSpawnPoints);

            if (initialSpawnPoints.isEmpty()) break;

            randomSpawn = initialSpawnPoints.remove(0);

            player.teleport(randomSpawn.getX() + 0.5, randomSpawn.getY(), randomSpawn.getZ() + 0.5);

            movementBlocker.disableMovement(player);
        }

        respawn.drawGrid(map, grid);

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, event -> !event.player().isCreativeLevelTwoOp());

        hooks.registerHook(ProjectileHooks.HIT_BLOCK,(projectile, hit) -> projectile.discard());

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {

            if (!(entity instanceof ServerPlayerEntity player)) return false;

            if (source.getSource() instanceof ProjectileEntity projectile) {

                Entity ownerEntity = projectile.getOwner();

                if (ownerEntity instanceof ServerPlayerEntity owner) {

                    if (owner == player) {
                        projectile.discard();
                        giveCrossbowToPlayer(owner);
                        return false;
                    }

                    killPlayer(player);
                    giveCrossbowToPlayer(player);
                    giveSwordToPlayer(player);

                    giveCrossbowToPlayer(owner);
                    owner.heal(20);
                    data.addScore(owner,1);

                    if (data.getScore(owner) == scoreLimit) {
                        win(owner);
                    }

                    getWorld().playSound(null, owner.getBlockPos(), SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.8f, 0.8f);
                    projectile.discard();
                    return false;
                }
                projectile.discard();
                return false;
            }

            if ((player.getHealth() - amount) <= 0) {

                if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
                    killPlayer(player);
                    giveCrossbowToPlayer(player);
                    giveSwordToPlayer(player);
                    return false;
                }

                killPlayer(player);
                giveCrossbowToPlayer(player);
                giveSwordToPlayer(player);

                data.addScore(attacker, 1);
                attacker.heal(20);

                if (data.getScore(attacker) == scoreLimit) {
                    win(attacker);
                }

                giveCrossbowToPlayer(attacker);
                getWorld().playSound(null, attacker.getBlockPos(), SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.8f, 0.8f);
                return false;
            }
            return true;
        });
    }

    @Override
    protected void ready() {

        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                -> damageSource.isOf(DamageTypes.ARROW) || damageSource.isOf(DamageTypes.OUTSIDE_BORDER) || damageSource.isOf(DamageTypes.PLAYER_ATTACK)));

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

        respawn.respawn(spawnPoints, player, gameHandle, grid);
    }

    private void giveCrossbowToPlayer(ServerPlayerEntity player) {

        ItemStack stack = new ItemStack(Items.CROSSBOW);

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtList nbtList = new NbtList();
        NbtCompound arrow = new NbtCompound();

        arrow.putInt("Count", 1);
        arrow.putString("id", "minecraft:arrow");
        nbtList.add(arrow);

        nbt.putBoolean("Charged", true);
        nbt.put("ChargedProjectiles", nbtList);

        stack.setCustomName(Text.translatable("item.minecraft.crossbow")
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

        ItemStackUtil.setUnbreakable(stack, true);

        PlayerInventory inventory = player.getInventory();
        inventory.setStack(1, stack);
    }

    private void giveSwordToPlayer(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);

        stack.setCustomName(Text.translatable("item.minecraft.iron_sword")
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

        ItemStackUtil.setUnbreakable(stack, true);

        PlayerInventory inventory = player.getInventory();
        inventory.setStack(0, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 0);
    }

    public void loadSpawnPoints(GameMap map, ArrayList<BlockPos> spawnPoints) {
        int spawnNumber = (Objects.requireNonNull(map.getProperty("random_spawn_number"), "spawn number not configured"));
        JSONArray jsonSpawns = Objects.requireNonNull(map.getProperty("random_spawns"), "spawnpoints not configured");

        while (spawnNumber > 0) {
            spawnNumber -= 1;
            spawnPoints.add(MapUtil.readBlockPos((JSONArray) jsonSpawns.get(spawnNumber)));
        }
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
    }
}
