package work.lclpnet.ap2.game.spleef;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.data.EliminationDataContainer;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.player.PlayerDeathCallback;
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

public class SpleefInstance extends DefaultGameInstance {

    private static final int WORLD_BORDER_DELAY = Ticks.seconds(40);
    private static final int WORLD_BORDER_TIME = Ticks.seconds(20);
    private final EliminationDataContainer data = new EliminationDataContainer();

    public SpleefInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        setDefaultGameMode(GameMode.SURVIVAL);
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void prepare() {
        Participants participants = gameHandle.getParticipants();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerDeathCallback.HOOK, (player, damageSource) -> {
            if (!participants.isParticipating(player)) return;

            data.eliminated(player);
            participants.remove(player);
        });

        PlayerUtil playerUtil = gameHandle.getPlayerUtil();
        playerUtil.setDefaultGameMode(GameMode.SURVIVAL);

        hooks.registerHook(PlayerSpawnLocationCallback.HOOK, data
                -> playerUtil.resetPlayer(data.getPlayer()));
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                World world = entity.getWorld();
                BlockState state = world.getBlockState(pos);

                return state.isOf(Blocks.SNOW_BLOCK);
            });

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource)
                    -> damageSource.isOf(DamageTypes.LAVA) || damageSource.isOf(DamageTypes.OUTSIDE_BORDER));
        });

        TranslationService translations = gameHandle.getTranslations();

        giveShovelsToPlayers(translations);

        scheduleSuddenDeath();
    }

    private void scheduleSuddenDeath() {
        TaskScheduler scheduler = gameHandle.getScheduler();

        scheduler.timeout(() -> {
            WorldBorderManager manager = gameHandle.getWorldBorderManager();
            manager.setupWorldBorder(getWorld());

            WorldBorder worldBorder = manager.getWorldBorder();
            worldBorder.setCenter(0.5, 0.5);
            worldBorder.setSize(31);
            worldBorder.setSafeZone(0);
            worldBorder.setDamagePerBlock(0.8);
            worldBorder.interpolateSize(worldBorder.getSize(), 1, WORLD_BORDER_TIME * 50L);

            for (ServerPlayerEntity player : PlayerLookup.world(getWorld())) {
                player.playSound(SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 1, 0);
            }
        }, WORLD_BORDER_DELAY);

        scheduler.timeout(() -> {
            for (ServerPlayerEntity player : gameHandle.getParticipants()) {
                removeBlocksUnder(player);
            }
        }, WORLD_BORDER_DELAY + WORLD_BORDER_TIME + Ticks.seconds(5));
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        var participants = gameHandle.getParticipants().getAsSet();
        if (participants.size() > 1) return;

        var winner = participants.stream().findAny();
        winner.ifPresent(data::eliminated);  // the winner also has to be tracked

        win(winner.orElse(null));
    }

    private void giveShovelsToPlayers(TranslationService translations) {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.IRON_SHOVEL);

            stack.setCustomName(translations.translateText(player, "game.ap2.shovel")
                    .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

            ItemStackUtil.setUnbreakable(stack, true);

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }

    private void removeBlocksUnder(ServerPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int x = playerPos.getX();
        int y = playerPos.getY();
        int z = playerPos.getZ();

        ServerWorld world = getWorld();
        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : BlockPos.iterate(x - 1, y - 2, z - 1, x + 1, y, z + 1)) {
            if (world.getBlockState(pos).isOf(Blocks.SNOW_BLOCK)) {
                world.setBlockState(pos, air);
            }
        }
    }
}
