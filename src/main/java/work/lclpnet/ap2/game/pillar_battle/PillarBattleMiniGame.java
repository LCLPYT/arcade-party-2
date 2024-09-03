package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.kibu.translate.text.LocalizedFormat;

public class PillarBattleMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(GameStartContext context) {
        return true;
    }

    @Override
    public Identifier getId() {
        return ArcadeParty.identifier("pillar_battle");
    }

    @Override
    public GameType getType() {
        return GameType.FFA;
    }

    @Override
    public String getAuthor() {
        return ApConstants.PERSON_LCLP;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.PURPUR_PILLAR);
    }

    @Override
    public Object[] getDescriptionArguments() {
        return new Object[] {LocalizedFormat.format("%.1f", PillarBattleInstance.RANDOM_ITEM_DELAY_TICKS / 20f)};
    }

    @Override
    public MiniGameInstance createInstance(MiniGameHandle gameHandle) {
        return new PillarBattleInstance(gameHandle);
    }
}
