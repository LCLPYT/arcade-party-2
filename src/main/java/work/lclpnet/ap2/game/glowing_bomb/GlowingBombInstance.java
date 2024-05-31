package work.lclpnet.ap2.game.glowing_bomb;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
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
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.UUID;

public class GlowingBombInstance extends EliminationGameInstance implements MapBootstrapFunction {

    private static final int MIN_BOMB_GLOW_STONE = 1;
    private static final int MAX_BOMB_GLOW_STONE = 3;
    private static final int MIN_BOMB_FUSE_TICKS = Ticks.seconds(7);
    private static final int MAX_BOMB_FUSE_TICKS = Ticks.seconds(18);
    private final Random random = new Random();
    private final SimpleMovementBlocker movementBlocker;
    private GbManager manager = null;
    private Scene scene = null;
    private GbBomb bomb = null;
    private boolean mayPass = false;

    public GlowingBombInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        disableTeleportEliminated();

        movementBlocker = new SimpleMovementBlocker(gameHandle.getGameScheduler());
        movementBlocker.setModifySpeedAttribute(false);
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        manager = new GbManager(world, map, random, gameHandle.getParticipants(), this::onAnchorFilled);
        manager.setupAnchors();
    }

    @Override
    protected void prepare() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        movementBlocker.init(hooks);

        manager.teleportPlayers();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.disableMovement(player);
        }

        useTaskDisplay();
    }

    @Override
    protected void ready() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !participants.isParticipating(serverPlayer)) {
                return TypedActionResult.pass(ItemStack.EMPTY);
            }

            if (serverPlayer.getStackInHand(hand).isOf(Items.GLOWSTONE)) {
                if (manager.hasBomb(serverPlayer)) {
                    passBomb(serverPlayer);
                }

                return TypedActionResult.fail(ItemStack.EMPTY);
            }

            return TypedActionResult.pass(ItemStack.EMPTY);
        });

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

        bomb = new GbBomb(this::onBombYielded);
        bomb.scale.set(0.4);
        bomb.position.set(pos.getX(), pos.getY(), pos.getZ());

        int amount = MIN_BOMB_GLOW_STONE + random.nextInt(Math.max(1, MAX_BOMB_GLOW_STONE - MIN_BOMB_GLOW_STONE + 1));
        bomb.setGlowStoneAmount(amount, random);

        ServerWorld world = getWorld();

        scene = new Scene(world);
        scene.add(bomb);

        TaskScheduler scheduler = gameHandle.getGameScheduler();
        scene.animate(1, scheduler);

        int fuseTicks = MIN_BOMB_FUSE_TICKS + random.nextInt(MAX_BOMB_FUSE_TICKS - MIN_BOMB_FUSE_TICKS + 1);
        scheduler.timeout(this::bombTimerExpired, fuseTicks);

        double x = pos.getX(), y = pos.getY(), z = pos.getZ();

        world.playSound(null, x, y, z, SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.HOSTILE, 0.75f, 0.5f);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 15, 0.1, 0.1, 0.1, 0.2);

        mayPass = true;
        manager.bombHolder().ifPresent(this::onAcquiredBomb);
    }

    private void onAcquiredBomb(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.GLOWSTONE, bomb != null ? bomb.getGlowStoneAmount() : 1);
        stack.set(DataComponentTypes.CUSTOM_NAME, gameHandle.getTranslations().translateText(player, "game.ap2.glowing_bomb.pass")
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

        player.getInventory().setStack(4, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 4);
    }

    private void onPassedBomb(ServerPlayerEntity player) {
        player.getInventory().setStack(4, ItemStack.EMPTY);
    }

    private void passBomb(ServerPlayerEntity player) {
        if (winManager.isGameOver() || !mayPass) return;

        ServerPlayerEntity nextHolder = manager.nextBombHolder();

        if (nextHolder == null) {
            nextHolder = player;
        }

        onPassedBomb(player);
        onAcquiredBomb(nextHolder);

        if (nextHolder == player) return;

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
        Vector3d pos = bomb.worldPosition();
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
        gameHandle.getGameScheduler().timeout(this::spawnBomb, Ticks.seconds(4));
    }

    private void checkForWinner() {
        winManager.checkForWinner(gameHandle.getParticipants().stream(), resolver);
    }

    private void onBombYielded() {
        if (bomb != null) {
            Vector3d pos = bomb.worldPosition();
            double x = pos.x(), y = pos.y(), z = pos.z();

            ServerWorld world = getWorld();
            world.playSound(null, x, y, z, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.HOSTILE, 1f, 0f);
            world.spawnParticles(ParticleTypes.SMALL_FLAME, x, y, z, 20, 0.1, 0.1, 0.1, 0.1);

            scene.remove(bomb);
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
}
