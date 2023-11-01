package work.lclpnet.ap2.game.treasure_hunter;

import mglobby.org.apache.commons.compress.compressors.lz77support.LZ77Compressor;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.Random;

public class TreasureHunterInstance extends DefaultGameInstance {

    private final BlockPos mapCenter = new BlockPos(0,64,0); //add Y from center Variable in map.json
    private final Integer maxRadius = 20; //add value from map.json
    private final Integer mapDepth = 8;

    public TreasureHunterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {}

    @Override
    protected DataContainer getData() {
        return null;
    }

    @Override
    protected void prepare() {
        placeChest();
        giveShovelsToPlayers();
    }

    @Override
    protected void ready() {

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
        final BlockPos corner1 = new BlockPos(mapCenter.getX()+maxRadius,mapCenter.getY()-mapDepth, mapCenter.getZ()-maxRadius);
        final BlockPos corner2 = new BlockPos(mapCenter.getX()-maxRadius,mapCenter.getY()-depth, mapCenter.getZ()+maxRadius);
        Iterable<BlockPos> chestArea = BlockPos.iterate(corner1,corner2);
        int areaSize = (maxRadius*2)*(maxRadius*2)*(mapDepth-depth);
        Random rand = new Random();
        int chestPos = rand.nextInt(areaSize);
        int i = 0;

        for (BlockPos block : chestArea) {
            if (!getWorld().getBlockState(block).isOf(Blocks.SAND)) {continue;}
            if (i==chestPos) {}//place chest, return
            i+=1;
        }
    }

    private int surfaceDepth() {
        if (mapDepth-2<=0) {return 1;};
        return 2;
    }
}
