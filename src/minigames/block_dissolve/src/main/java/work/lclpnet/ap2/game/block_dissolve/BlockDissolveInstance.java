package work.lclpnet.ap2.game.block_dissolve;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Random;

public class BlockDissolveInstance extends EliminationGameInstance {

    public static final int SNOWBALL_SECONDS = 6;
    private static final int MAX_SNOWBALLS = 6, WARNING_DELAY_TICKS = 70, WARNING_PERIOD_TICKS = 5, WARNING_AMOUNT = 150;
    private final LongList markedBlocks = new LongArrayList();
    private final Random random = new Random();
    private int nextSnowball = Ticks.seconds(3), tickOfSecond = 0, extraDissolvedThisSecond = 0,
            totalTime = 0, time = 0, dps = 0, warningTimer = 0;
    private boolean warning = false;
    private boolean physics = false;

    public BlockDissolveInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        useOldCombat();
    }

    @Override
    protected void prepare() {
        commons().gameRuleBuilder()
                .set(GameRules.RANDOM_TICK_SPEED, 0)
                .set(GameRules.SPREAD_VINES, false);

        useNoHealing();
        useSmoothDeath();
        useRemainingPlayersDisplay();

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.MOUNT);
            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> source.getDirectEntity() instanceof Projectile);
        });

        Object physics = getMap().getProperty("block_physics");

        if (physics instanceof Boolean p) {
            this.physics = p;
        }

        scanWorld();
    }

    @Override
    protected void go() {
        commons().whenBelowCriticalHeight().then(this::eliminate);

        startDissolve();
    }

    private void scanWorld() {
        BlockBox bounds = MapUtil.readBox(getMap().requireProperty("bounds"));

        ServerLevel world = getWorld();

        for (BlockPos pos : bounds) {
            BlockState state = world.getBlockState(pos);

            if (state.isAir()) continue;

            markedBlocks.add(pos.asLong());
        }
    }

    private void startDissolve() {
        gameHandle.getScheduler().interval(this::tick, 1);
    }

    private void tick(RunningTask info) {
        MinecraftServer server = gameHandle.getServer();

        totalTime++;

        if (nextSnowball-- <= 0) {
            nextSnowball = Ticks.seconds(SNOWBALL_SECONDS);

            giveSnowball();
        }

        if (warning) {
            warningTimer++;

            if (warningTimer % WARNING_PERIOD_TICKS == 0) {
                for (ServerPlayer player : PlayerLookup.all(server)) {
                    ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1f, 0f);
                }
            }

            if (warningTimer >= WARNING_DELAY_TICKS) {
                warning = false;
                warningTimer = 0;

                for (ServerPlayer player : PlayerLookup.all(server)) {
                    ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 1f, 0f);
                }

                for (int i = 0; i < WARNING_AMOUNT; i++) {
                    dissolveBlock();
                }
            }
        }

        boolean changeDps;

        if (dps < 20) {
            changeDps = totalTime % 45 == 0;
        } else if (dps < 30) {
            changeDps = totalTime % 60 == 0;
        } else {
            changeDps = totalTime % 85 == 0;
        }

        if (changeDps) {
            if (dps > 20 && dps % 5 == 0 && markedBlocks.size() >= 500) {
                warning = true;
            }

            dps++;
        }

        if (markedBlocks.isEmpty()) {
            info.cancel();
            return;
        }

        tickOfSecond++;

        if (++time >= (int) (20f / dps)) {
            time = 0;

            dissolveBlock();

            int extra = dps - 20;

            if (extraDissolvedThisSecond < extra) {
                int amount;

                if (tickOfSecond == 20) {
                    amount = extra - extraDissolvedThisSecond;
                } else {
                    amount = (int) (dps / 20f);
                }

                for (int j = 0; j < amount; j++) {
                    dissolveBlock();
                }

                extraDissolvedThisSecond += amount;
            }
        }

        if (tickOfSecond >= 20) {
            tickOfSecond = 0;
            extraDissolvedThisSecond = 0;
        }
    }

    private void dissolveBlock() {
        int size = markedBlocks.size();

        if (size == 0) return;

        int idx = random.nextInt(size);

        BlockPos pos = BlockPos.of(markedBlocks.removeLong(idx));
        ServerLevel world = getWorld();

        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

        if (physics) {
            flags |= Block.UPDATE_NEIGHBORS;
        } else {
            flags |= Block.UPDATE_KNOWN_SHAPE;
        }

        world.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);

        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;

        world.sendParticles(ParticleTypes.FLAME, x, y, z, 10, 0.2, 0.2, 0.2, 0.1);
        world.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 0.75f);
    }

    private void giveSnowball() {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            giveSnowball(player);
        }
    }

    private void giveSnowball(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(0);

        if (!stack.is(Items.SNOWBALL)) {
            stack = new ItemStack(Items.SNOWBALL);
        } else if (stack.getCount() >= MAX_SNOWBALLS) {
            return;
        } else {
            stack.grow(1);
        }

        inventory.setItem(0, stack);
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 2f);
    }
}
