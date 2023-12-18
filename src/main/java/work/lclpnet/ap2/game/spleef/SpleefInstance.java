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
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Objects;

public class SpleefInstance extends EliminationGameInstance {

    private static final int WORLD_BORDER_DELAY = Ticks.seconds(40);
    private static final int WORLD_BORDER_TIME = Ticks.seconds(20);

    public SpleefInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useSurvivalMode();
    }

    @Override
    protected void prepare() {
        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();
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
        TaskScheduler scheduler = gameHandle.getGameScheduler();

        scheduler.timeout(() -> {
            WorldBorder worldBorder = useWorldBorder();
            worldBorder.interpolateSize(worldBorder.getSize(), 1, WORLD_BORDER_TIME * 50L);

            for (ServerPlayerEntity player : PlayerLookup.world(getWorld())) {
                player.playSound(SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 1, 0);
            }
        }, WORLD_BORDER_DELAY);

        scheduler.timeout(this::removeBlocks, WORLD_BORDER_DELAY + WORLD_BORDER_TIME + Ticks.seconds(5));
    }

    private void removeBlocks() {
        ServerWorld world = getWorld();
        BlockState air = Blocks.AIR.getDefaultState();

        JSONArray areaJson = Objects.requireNonNull(getMap().getProperty("snow-area"), "Snow area undefined");
        var box = MapUtil.readBox(areaJson);

        for (BlockPos pos : BlockPos.iterate(box.first(), box.second())) {
            if (world.getBlockState(pos).isOf(Blocks.SNOW_BLOCK)) {
                world.setBlockState(pos, air);
            }
        }

        SoundHelper.playSound(gameHandle.getServer(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.AMBIENT, 0.8f, 1f);
    }

    private void giveShovelsToPlayers(TranslationService translations) {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.IRON_SHOVEL);

            stack.setCustomName(translations.translateText(player, "game.ap2.spleef.shovel")
                    .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

            ItemStackUtil.setUnbreakable(stack, true);

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }
}
