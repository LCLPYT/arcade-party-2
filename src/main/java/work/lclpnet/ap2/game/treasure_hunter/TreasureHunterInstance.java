package work.lclpnet.ap2.game.treasure_hunter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TreasureHunterInstance extends DefaultGameInstance {

    private final BlockPos mapCenter = new BlockPos(8,-54,8); //add Y from center Variable in map.json
    private final Integer mapDepth = 6;

    public TreasureHunterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        setDefaultGameMode(GameMode.SURVIVAL);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {}

    @Override
    protected DataContainer getData() {
        return null;
    }

    @Override
    protected void prepare() {
        ArrayList<ServerPlayerEntity> winner_list = new ArrayList<>();

        Participants participants = gameHandle.getParticipants();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {

            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!participants.isParticipating(serverPlayer)) return ActionResult.PASS;

            if (!world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.CHEST)) return ActionResult.PASS;

            if (winner_list.isEmpty()) { //doesn't work, win is still called every time
                win(serverPlayer);
                winner_list.add(serverPlayer);
                System.out.print(winner_list);
                return ActionResult.success(true);
            }

            return ActionResult.PASS;
        });

        placeChest();
        giveShovelsToPlayers();
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
            World world = entity.getWorld();
            BlockState state = world.getBlockState(pos);

            return state.isOf(Blocks.SAND);
        }));
    }

    private void giveShovelsToPlayers() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.IRON_SHOVEL);

            ItemStackUtil.setUnbreakable(stack, true);

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }
    private void placeChest() {

        int depth = surfaceDepth();
        ServerWorld world = getWorld(); //add value from map.json
        int maxRadius = 14;
        final BlockPos corner1 = new BlockPos(mapCenter.getX() + maxRadius,mapCenter.getY()-mapDepth, mapCenter.getZ() - maxRadius);
        final BlockPos corner2 = new BlockPos(mapCenter.getX() - maxRadius,mapCenter.getY()-depth, mapCenter.getZ() + maxRadius);
        List<BlockPos> chestAreaList = new ArrayList<>();
        Random rand = new Random();

        for (BlockPos block : BlockPos.iterate(corner1,corner2)) {
            if (world.getBlockState(block).isOf(Blocks.SAND)) {
                chestAreaList.add(block.toImmutable());
            }
        }

        int randomIndex = rand.nextInt(1,chestAreaList.size()-1);

        BlockPos chestPos = chestAreaList.get(randomIndex);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
        System.out.print("corner 1: " + corner1 + "\n");
        System.out.print("corner 2: " + corner2 + "\n");
        System.out.print("randomIndex: " + randomIndex + "\n");
        System.out.print("chestPos: " + chestPos + "\n");
    }

    private int surfaceDepth() {
        if (mapDepth-2<=0) {return 1;}
        return 2;
    }
}
