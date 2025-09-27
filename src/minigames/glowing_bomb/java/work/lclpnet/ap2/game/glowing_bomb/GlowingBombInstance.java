package work.lclpnet.ap2.game.glowing_bomb;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.glowing_bomb.data.GbAnchor;
import work.lclpnet.ap2.game.glowing_bomb.data.GbBomb;
import work.lclpnet.ap2.game.glowing_bomb.data.GbManager;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.gaco.scene.Scene;
import work.lclpnet.gaco.scene.ServerWorldMountContext;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.UUID;

public class GlowingBombInstance extends EliminationGameInstance implements MapBootstrapFunction {

    private static final int BOMB_PASS_COST = 40;
    private static final int INITIAL_CREDITS = BOMB_PASS_COST * 2;
    private static final int MINIMUM_BOMB_PASS_TICKS = 10;
    private static final int CREDITS_PER_TICK = 1;
    private final Random random = new Random();
    private final SimpleMovementBlocker movementBlocker;
    private final Object2IntMap<UUID> credits = new Object2IntOpenHashMap<>();
    private final int initialPlayerCount;
    private GbManager manager = null;
    private Scene scene = null;
    private GbBomb bomb = null;
    private boolean mayPass = false;
    private boolean wasPassed = false;
    private int time = 0;
    private TaskHandle bombDelayedSpawn = null;

    public GlowingBombInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        initialPlayerCount = gameHandle.getParticipants().count();

        disableTeleportEliminated();

        movementBlocker = new SimpleMovementBlocker(gameHandle.getGameScheduler());
        movementBlocker.setModifySpeedAttribute(false);
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(@NotNull ServerWorld world, @NotNull GameMap map) {
        manager = new GbManager(world, map, random, gameHandle.getParticipants(), this::onAnchorFilled);
        manager.setupAnchors();
    }

    @Override
    protected void prepare() {
        HookRegistrar hooks = gameHandle.getHooks();

        movementBlocker.init(hooks);

        manager.teleportPlayers();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.disableMovement(player);
        }

        useTaskDisplay();
    }

    @Override
    protected void ready() {
        HookRegistrar hooks = gameHandle.getHooks();
        Participants participants = gameHandle.getParticipants();

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !participants.isParticipating(serverPlayer)) {
                return ActionResult.PASS;
            }

            ItemStack stack = serverPlayer.getStackInHand(hand);

            if (stack.isOf(Items.GLOWSTONE)) {
                if (manager.hasBomb(serverPlayer) && !player.getItemCooldownManager().isCoolingDown(stack)) {
                    passBomb(serverPlayer);
                }

                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        gameHandle.getGameScheduler().interval(this::tickCredits, 1);

        scene = new Scene(new ServerWorldMountContext(getWorld()));
        scene.animate(1, gameHandle.getGameScheduler());

        spawnBomb();
    }

    @Override
    protected void onEliminated(ServerPlayerEntity player) {
        movementBlocker.enableMovement(player);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        manager.removeAnchorOf(player);
        super.participantRemoved(player);
    }

    private void spawnBomb() {
        if (!manager.assignBomb()) {
            checkForWinner();
            return;
        }

        Vec3d pos = manager.bombLocation();

        if (pos == null) {
            checkForWinner();
            return;
        }

        bomb = new GbBomb(scene, this::onBombYielded);
        bomb.scale.set(0.4);
        bomb.position.set(pos.getX(), pos.getY(), pos.getZ());

        int amount = randomAmount();
        bomb.setGlowStoneAmount(amount, random);

        ServerWorld world = getWorld();

        scene.add(bomb);

        TaskScheduler scheduler = gameHandle.getGameScheduler();

        int fuseTicks = randomFuseTicks();
        scheduler.timeout(this::bombTimerExpired, fuseTicks);

        double x = pos.getX(), y = pos.getY(), z = pos.getZ();

        world.playSound(null, x, y, z, SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.HOSTILE, 0.75f, 0.5f);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 15, 0.1, 0.1, 0.1, 0.2);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            credits.put(player.getUuid(), INITIAL_CREDITS);
        }

        time = 0;
        wasPassed = false;
        mayPass = true;

        manager.bombHolder().ifPresent(this::onAcquiredBomb);
    }

    private int randomFuseTicks() {
        int minFuseTicks = minFuseTicks();
        int maxFuseTicks;

        if (initialPlayerCount <= 5) {
            maxFuseTicks = Ticks.seconds(18);
        } else if (initialPlayerCount <= 10) {
            // avg 10s
            maxFuseTicks = Ticks.seconds(14);
        } else {
            // avg 8.75s
            maxFuseTicks = Ticks.seconds(12);
        }

        return random.nextInt(minFuseTicks, maxFuseTicks + 1);
    }

    private int minFuseTicks() {
        if (initialPlayerCount <= 5) {
            return Ticks.seconds(7);
        }

        if (initialPlayerCount <= 10) {
            return Ticks.seconds(6);
        }

        return Ticks.seconds(5) + 10;
    }

    private int randomAmount() {
        if (initialPlayerCount <= 5) {
            // 4 player worst case: 3 * 4 * 18s + 3 * 18s = 270s = 4.5min
            // 4 player avg case: 3 * 2 * 12.5s + 12.5s = 87.5s = 1.5min
            // 4 player best case: 3 * 2 * 7s = 42s
            return random.nextInt(1, 4);
        }

        if (initialPlayerCount <= 10) {
            // 10 player worst case: 9 * 2 * 14s + 14s = 266s = 4.4min
            // 10 player avg case: 9 * 2 * 10s + 10s = 190s = 3.17min
            // 10 player best case: 9 * 2 * 6s = 108s = 1.8min
            return random.nextInt(2, 4);
        }

        // 12 player worst case: 11 * 2 * 12s + 12s = 264s = 4.4min
        // 12 player avg case: 11 * 2 * 8.75s + 8.75s = 201s = 3.4min
        // 12 player best case: 11 * 5.5s = 60s = 1min
        return random.nextInt(2, 5);
    }

    private void onAcquiredBomb(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.GLOWSTONE, bomb != null ? bomb.getGlowStoneAmount() : 1);
        stack.set(DataComponentTypes.CUSTOM_NAME, gameHandle.getTranslations().translateText(player, "game.ap2.glowing_bomb.pass")
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

        player.getInventory().setStack(4, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 4);

        int creditCount = credits.getOrDefault(player.getUuid(), 0);
        int cooldown = MINIMUM_BOMB_PASS_TICKS + Math.max(0, BOMB_PASS_COST - creditCount);

        Identifier cooldownGroup = Registries.ITEM.getId(Items.GLOWSTONE);

        player.getItemCooldownManager().set(cooldownGroup, cooldown);
    }

    private void onPassedBomb(ServerPlayerEntity player) {
        player.getInventory().setStack(4, ItemStack.EMPTY);
    }

    private void passBomb(ServerPlayerEntity player) {
        if (winManager.isGameOver() || !mayPass) return;

        // remove credits
        UUID uuid = player.getUuid();
        int creditCount = credits.getOrDefault(uuid, 0);

        if (creditCount < BOMB_PASS_COST) return;

        credits.put(uuid, Math.max(0, creditCount - BOMB_PASS_COST));

        // pass bomb
        ServerPlayerEntity nextHolder = manager.nextBombHolder();

        if (nextHolder == null) {
            nextHolder = player;
        }

        onPassedBomb(player);
        onAcquiredBomb(nextHolder);

        if (nextHolder == player) return;

        wasPassed = true;

        if (bomb == null) return;

        Vec3d pos = manager.bombLocation();

        if (pos == null) return;

        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        bomb.position.set(x, y, z);
        getWorld().playSound(null, x, y, z, SoundEvents.BLOCK_BEEHIVE_ENTER, SoundCategory.HOSTILE, 0.75f, 1.4f);
    }

    private void bombTimerExpired() {
        mayPass = false;
        manager.bombHolder().ifPresent(this::onPassedBomb);

        ServerWorld world = getWorld();
        Vector3d pos = bomb.worldTranslation();
        double x = pos.x(), y = pos.y(), z = pos.z();

        world.playSound(null, x, y, z, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.HOSTILE, 0.9f, 1.0f);
        world.spawnParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 1);
        world.spawnParticles(ParticleTypes.SMALL_FLAME, x, y, z, 20, 0.1, 0.1, 0.1, 0.1);

        GbAnchor anchor = manager.bombAnchor();

        if (anchor == null) {
            checkForWinnerOrNext();
            return;
        }

        bomb.yieldGlowStone(manager, anchor);
    }

    private void checkForWinnerOrNext() {
        if (gameHandle.getParticipants().count() < 2) {
            checkForWinner();
            return;
        }

        delayNextBomb();
    }

    private void delayNextBomb() {
        if (bombDelayedSpawn != null) {
            bombDelayedSpawn.cancel();
        }

        bombDelayedSpawn = gameHandle.getGameScheduler().timeout(this::spawnBomb, Ticks.seconds(4));
    }

    private void checkForWinner() {
        winManager.checkForLastRemaining();
    }

    private void onBombYielded() {
        if (bomb != null) {
            Vector3d pos = bomb.worldTranslation();
            double x = pos.x(), y = pos.y(), z = pos.z();

            ServerWorld world = getWorld();
            world.playSound(null, x, y, z, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.HOSTILE, 1f, 0f);
            world.spawnParticles(ParticleTypes.SMALL_FLAME, x, y, z, 20, 0.1, 0.1, 0.1, 0.1);

            scene.remove(bomb);
            bomb = null;
        }

        delayNextBomb();
    }

    private void onAnchorFilled(GbAnchor anchor) {
        gameHandle.getGameScheduler().timeout(() -> explodeAnchor(anchor), 30);
    }

    private void explodeAnchor(GbAnchor anchor) {
        if (winManager.isGameOver()) return;

        ServerWorld world = getWorld();

        Vec3d pos = anchor.pos();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;

        world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 200, 1, 1, 1, 0.5);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.9f, 1.2f);

        manager.removeAnchor(anchor);

        UUID owner = anchor.owner();
        ServerPlayerEntity player = gameHandle.getServer().getPlayerManager().getPlayer(owner);

        if (player == null) {
            checkForWinnerOrNext();
            return;
        }

        eliminate(player);

        if (winManager.isGameOver()) return;

        delayNextBomb();
    }

    private void tickCredits() {
        // pause credit acquisition if the bomb is currently not passable
        if (!mayPass) return;

        time++;

        // don't grant credits if the bomb wasn't passed yet and couldn't have exploded yet because of the minimum fuse time
        if (!wasPassed && time < minFuseTicks()) return;

        // grant credits each tick
        manager.bombHolder().ifPresent(player -> credits.computeInt(player.getUuid(), (uuid, count) -> {
            if (count == null) count = 0;

            return count + CREDITS_PER_TICK;
        }));
    }
}
