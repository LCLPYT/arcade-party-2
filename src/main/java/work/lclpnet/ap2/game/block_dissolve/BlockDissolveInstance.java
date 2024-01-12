package work.lclpnet.ap2.game.block_dissolve;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Random;

public class BlockDissolveInstance extends EliminationGameInstance {

    public static final int SNOWBALL_SECONDS = 6;
    private static final int MAX_SNOWBALLS = 6, WARNING_DELAY_TICKS = 70, WARNING_PERIOD_TICKS = 5, WARNING_AMOUNT = 100;
    private final LongList markedBlocks = new LongArrayList();
    private final Random random = new Random();
    private int nextSnowball = Ticks.seconds(3), tickOfSecond = 0, extraDissolvedThisSecond = 0,
            totalTime = 0, time = 0, dps = 0, warningTimer = 0;
    private boolean warning = false;

    public BlockDissolveInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        useOldCombat();
    }

    @Override
    protected void prepare() {
        MinecraftServer server = gameHandle.getServer();
        GameRules gameRules = getWorld().getGameRules();

        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, server);
        gameRules.get(GameRules.DO_VINES_SPREAD).set(false, server);

        useNoHealing();
        useSmoothDeath();
        useRemainingPlayersDisplay();

        scanWorld();
    }

    @Override
    protected void ready() {
        commons().whenBelowCriticalHeight().then(this::eliminate);

        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source)
                -> source.getSource() instanceof ProjectileEntity));

        startDissolve();
    }

    private void scanWorld() {
        BlockBox bounds = MapUtil.readBox(getMap().requireProperty("bounds"));

        ServerWorld world = getWorld();

        for (BlockPos pos : bounds) {
            BlockState state = world.getBlockState(pos);

            if (state.isAir()) continue;

            markedBlocks.add(pos.asLong());
        }
    }

    private void startDissolve() {
        gameHandle.getGameScheduler().interval(this::tick, 1);
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
                for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 1f, 0f);
                }
            }

            if (warningTimer >= WARNING_DELAY_TICKS) {
                warning = false;
                warningTimer = 0;

                for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                    player.playSound(SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.BLOCKS, 1f, 0f);
                }

                for (int i = 0; i < WARNING_AMOUNT; i++) {
                    dissolveBlock();
                }
            }
        }

        boolean changeDps;

        if (dps < 20) {
            changeDps = totalTime % 50 == 0;
        } else if (dps < 30) {
            changeDps = totalTime % 70 == 0;
        } else {
            changeDps = totalTime % 100 == 0;
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

        BlockPos pos = BlockPos.fromLong(markedBlocks.removeLong(idx));
        ServerWorld world = getWorld();

        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);

        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;

        world.spawnParticles(ParticleTypes.FLAME, x, y, z, 10, 0.2, 0.2, 0.2, 0.1);
        world.playSound(null, x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.3f, 0.75f);
    }

    private void giveSnowball() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            giveSnowball(player);
        }
    }

    private void giveSnowball(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getStack(0);

        if (!stack.isOf(Items.SNOWBALL)) {
            stack = new ItemStack(Items.SNOWBALL);
        } else if (stack.getCount() >= MAX_SNOWBALLS) {
            return;
        } else {
            stack.increment(1);
        }

        inventory.setStack(0, stack);
        player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.6f, 2f);
    }
}
