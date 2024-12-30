package work.lclpnet.ap2.game.aim_master;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import work.lclpnet.ap2.impl.util.IndexedSet;

public class BlockOptions {

    public IndexedSet<Block> getBlockOptions() {
        IndexedSet<Block> blockOptions = new IndexedSet<>();
        setBlockOptions(blockOptions);
        return blockOptions;
    }

    private void setBlockOptions(IndexedSet<Block> blockOptions) {
        blockOptions.add(Blocks.OAK_WOOD);
        blockOptions.add(Blocks.BIRCH_WOOD);
        blockOptions.add(Blocks.MANGROVE_PLANKS);
        blockOptions.add(Blocks.OAK_PLANKS);
        blockOptions.add(Blocks.BAMBOO_BLOCK);
        blockOptions.add(Blocks.MOSSY_COBBLESTONE);
        blockOptions.add(Blocks.REINFORCED_DEEPSLATE);
        blockOptions.add(Blocks.MUD_BRICKS);
        blockOptions.add(Blocks.BRICKS);
        blockOptions.add(Blocks.CHISELED_RED_SANDSTONE);
        blockOptions.add(Blocks.NETHER_BRICKS);
        blockOptions.add(Blocks.RED_NETHER_BRICKS);
        blockOptions.add(Blocks.SEA_LANTERN);
        blockOptions.add(Blocks.DARK_PRISMARINE);
        blockOptions.add(Blocks.GILDED_BLACKSTONE);
        blockOptions.add(Blocks.PURPUR_BLOCK);
        blockOptions.add(Blocks.END_STONE);
        blockOptions.add(Blocks.GOLD_BLOCK);
        blockOptions.add(Blocks.DIAMOND_BLOCK);
        blockOptions.add(Blocks.QUARTZ_BLOCK);
        blockOptions.add(Blocks.WAXED_COPPER_BLOCK);
        blockOptions.add(Blocks.WAXED_WEATHERED_COPPER);
        blockOptions.add(Blocks.WAXED_OXIDIZED_COPPER);
        blockOptions.add(Blocks.RED_WOOL);
        blockOptions.add(Blocks.GREEN_WOOL);
        blockOptions.add(Blocks.CYAN_CONCRETE);
        blockOptions.add(Blocks.MAGENTA_CONCRETE);
        blockOptions.add(Blocks.YELLOW_CONCRETE);
        blockOptions.add(Blocks.MAGENTA_GLAZED_TERRACOTTA);
        blockOptions.add(Blocks.WHITE_GLAZED_TERRACOTTA);
        blockOptions.add(Blocks.BLACK_GLAZED_TERRACOTTA);
        blockOptions.add(Blocks.TINTED_GLASS);
        blockOptions.add(Blocks.MOSS_BLOCK);
        blockOptions.add(Blocks.GRASS_BLOCK);
        blockOptions.add(Blocks.PACKED_ICE);
        blockOptions.add(Blocks.MAGMA_BLOCK);
        blockOptions.add(Blocks.CRYING_OBSIDIAN);
        blockOptions.add(Blocks.OBSIDIAN);
        blockOptions.add(Blocks.SOUL_SOIL);
        blockOptions.add(Blocks.EMERALD_ORE);
        blockOptions.add(Blocks.NETHER_QUARTZ_ORE);
        blockOptions.add(Blocks.ANCIENT_DEBRIS);
        blockOptions.add(Blocks.RAW_IRON_BLOCK);
        blockOptions.add(Blocks.RAW_GOLD_BLOCK);
        blockOptions.add(Blocks.SHROOMLIGHT);
        blockOptions.add(Blocks.WARPED_WART_BLOCK);
        blockOptions.add(Blocks.RED_MUSHROOM_BLOCK);
        blockOptions.add(Blocks.CHERRY_LEAVES);
        blockOptions.add(Blocks.SPRUCE_LEAVES);
        blockOptions.add(Blocks.FLOWERING_AZALEA_LEAVES);
        blockOptions.add(Blocks.SLIME_BLOCK);
        blockOptions.add(Blocks.DRIED_KELP_BLOCK);
        blockOptions.add(Blocks.WET_SPONGE);
        blockOptions.add(Blocks.MELON);
        blockOptions.add(Blocks.JACK_O_LANTERN);
        blockOptions.add(Blocks.BEEHIVE);
        blockOptions.add(Blocks.HAY_BLOCK);
        blockOptions.add(Blocks.PEARLESCENT_FROGLIGHT);
        blockOptions.add(Blocks.SCULK);
        blockOptions.add(Blocks.REDSTONE_LAMP);
        blockOptions.add(Blocks.CRAFTING_TABLE);
        blockOptions.add(Blocks.FLETCHING_TABLE);
        blockOptions.add(Blocks.FURNACE);
        blockOptions.add(Blocks.NOTE_BLOCK);
        blockOptions.add(Blocks.BEACON);
        blockOptions.add(Blocks.LODESTONE);
        blockOptions.add(Blocks.BEE_NEST);
        blockOptions.add(Blocks.BOOKSHELF);
        blockOptions.add(Blocks.BARREL);
        blockOptions.add(Blocks.RESPAWN_ANCHOR);
        blockOptions.add(Blocks.CRAFTER);
        blockOptions.add(Blocks.STICKY_PISTON);
        blockOptions.add(Blocks.OBSERVER);
        blockOptions.add(Blocks.TNT);
        blockOptions.add(Blocks.TARGET);
    }
}
