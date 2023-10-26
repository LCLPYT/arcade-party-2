package work.lclpnet.ap2.game.spleef;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import work.lclpnet.ap2.api.base.Participants;
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
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

public class SpleefInstance extends DefaultGameInstance {

    private final EliminationDataContainer data = new EliminationDataContainer();

    public SpleefInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
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

        hooks.registerHook(PlayerSpawnLocationCallback.HOOK, data
                -> PlayerUtil.resetPlayer(data.getPlayer(), PlayerUtil.Preset.SPECTATOR));
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                World world = entity.getWorld();
                BlockState state = world.getBlockState(pos);

                return state.isOf(Blocks.SNOW_BLOCK);
            });

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) -> damageSource.isOf(DamageTypes.LAVA));
        });

        TranslationService translations = gameHandle.getTranslations();

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

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        var participants = gameHandle.getParticipants().getAsSet();
        if (participants.size() > 1) return;

        var winner = participants.stream().findAny();
        winner.ifPresent(data::eliminated);  // the winner also has to be tracked

        win(winner.orElse(null));
    }
}
