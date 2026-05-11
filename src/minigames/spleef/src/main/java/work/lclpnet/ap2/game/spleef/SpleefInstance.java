package work.lclpnet.ap2.game.spleef;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.Objects;

import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class SpleefInstance extends EliminationGameInstance {

    private static final int WORLD_BORDER_DELAY = Ticks.seconds(40);
    private static final int WORLD_BORDER_TIME = Ticks.seconds(30);

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
    protected void go() {
        gameHandle.protect(config -> {
            ProtectionTypes.BREAK_BLOCKS.allow(config, (entity, pos) -> {
                Level world = entity.level();
                BlockState state = world.getBlockState(pos);

                return state.is(Blocks.SNOW_BLOCK);
            });

            ProtectionTypes.ALLOW_DAMAGE.allow(config, (_, damageSource)
                    -> damageSource.is(DamageTypes.LAVA) || damageSource.is(DamageTypes.OUTSIDE_BORDER));
        });

        Translations translations = gameHandle.getTranslations();

        giveShovelsToPlayers(translations);

        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, Ticks.seconds(5))
                .then(this::removeBlocks);
    }

    private void removeBlocks() {
        ServerLevel world = getWorld();
        BlockState air = Blocks.AIR.defaultBlockState();

        JSONArray areaJson = Objects.requireNonNull(getMap().getProperty("snow-area"), "Snow area undefined");
        BlockBox box = MapUtil.readBox(areaJson);

        for (BlockPos pos : BlockPos.betweenClosed(box.first(), box.second())) {
            if (world.getBlockState(pos).is(Blocks.SNOW_BLOCK)) {
                world.setBlockAndUpdate(pos, air);
            }
        }

        SoundHelper.playSound(gameHandle.getServer(), SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 0.8f, 1f);
    }

    private void giveShovelsToPlayers(Translations translations) {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            ItemStack stack = unbreakable(new ItemStack(Items.IRON_SHOVEL));

            stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.spleef.shovel")
                    .styled(style -> style.withItalic(false).applyFormat(ChatFormatting.GOLD)));

            Inventory inventory = player.getInventory();
            inventory.setItem(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }
}
