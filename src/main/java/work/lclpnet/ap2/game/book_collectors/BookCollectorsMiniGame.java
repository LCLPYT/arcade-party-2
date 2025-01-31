package work.lclpnet.ap2.game.book_collectors;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;

public class BookCollectorsMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return false;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return true;
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new BookCollectorsInstance(gameHandle);
    }

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("book_collectors");
    }

    @Override
    public GameType getType() {
        return GameType.TEAM;
    }

    @Override
    public String getAuthor() {
        return ApConstants.PERSON_BOPS;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.WRITTEN_BOOK);
    }
}
