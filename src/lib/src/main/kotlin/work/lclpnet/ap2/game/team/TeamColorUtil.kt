package work.lclpnet.ap2.game.team

import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks.*
import work.lclpnet.ap2.game.team.DyeTeamKey.*

fun DyeTeamKey.dyeColor(): DyeColor = when (this) {
    WHITE -> DyeColor.WHITE
    LIGHT_GRAY -> DyeColor.LIGHT_GRAY
    DARK_GRAY -> DyeColor.GRAY
    BLACK -> DyeColor.BLACK
    BROWN -> DyeColor.BROWN
    RED -> DyeColor.RED
    BLUE -> DyeColor.BLUE
    ORANGE -> DyeColor.ORANGE
    YELLOW -> DyeColor.YELLOW
    PURPLE -> DyeColor.PURPLE
    LIME -> DyeColor.LIME
    DARK_GREEN -> DyeColor.GREEN
    CYAN -> DyeColor.CYAN
    LIGHT_BLUE -> DyeColor.LIGHT_BLUE
    MAGENTA -> DyeColor.MAGENTA
    PINK -> DyeColor.PINK
}

fun DyeTeamKey.woolBlock(): Block = WOOL.pick(dyeColor())

fun DyeTeamKey.carpetBlock(): Block = CARPET.pick(dyeColor())

fun DyeTeamKey.concreteBlock(): Block = CONCRETE.pick(dyeColor())

fun DyeTeamKey.concretePowderBlock(): Block = CONCRETE_POWDER.pick(dyeColor())

fun DyeTeamKey.terracottaBlock(): Block = DYED_TERRACOTTA.pick(dyeColor())

fun DyeTeamKey.glazedTerracottaBlock(): Block = GLAZED_TERRACOTTA.pick(dyeColor())

fun DyeTeamKey.stainedGlassBlock(): Block = STAINED_GLASS.pick(dyeColor())

fun DyeTeamKey.stainedGlassPaneBlock(): Block = STAINED_GLASS_PANE.pick(dyeColor())

fun DyeTeamKey.bedBlock(): Block = BED.pick(dyeColor())

fun DyeTeamKey.shulkerBoxBlock(): Block = DYED_SHULKER_BOX.pick(dyeColor())

fun DyeTeamKey.candleBlock(): Block = DYED_CANDLE.pick(dyeColor())

fun DyeTeamKey.candleCakeBlock(): Block = DYED_CANDLE_CAKE.pick(dyeColor())

fun DyeTeamKey.bannerBlock(): Block = BANNER.pick(dyeColor())

fun DyeTeamKey.wallBannerBlock(): Block = WALL_BANNER.pick(dyeColor())
