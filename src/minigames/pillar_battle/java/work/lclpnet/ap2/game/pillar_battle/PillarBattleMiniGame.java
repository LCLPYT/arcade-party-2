package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.*;
import work.lclpnet.kibu.translate.text.LocalizedFormat;

public class PillarBattleMiniGame implements MiniGame {

    @Override
    public boolean canBeFinale(@NotNull GameStartContext context) {
        return true;
    }

    @Override
    public boolean canBePlayed(@NotNull GameStartContext context) {
        return true;
    }

    @Override
    public @NotNull Identifier getId() {
        return ApConstants.identifier("pillar_battle");
    }

    @Override
    public @NotNull GameType getType() {
        return GameType.FFA;
    }

    @Override
    public @NotNull String getAuthor() {
        return ApConstants.PERSON_LCLP;
    }

    @Override
    public @NotNull ItemStack getIcon(@NotNull DynamicRegistryManager manager) {
        return new ItemStack(Items.PURPUR_PILLAR);
    }

    @Override
    public Object[] getDescriptionArguments() {
        return new Object[] {LocalizedFormat.format("%.1f", PillarBattleInstance.RANDOM_ITEM_DELAY_TICKS / 20f)};
    }

    @Override
    public @NotNull MiniGameInstance createInstance(@NotNull MiniGameHandle gameHandle) {
        return new PillarBattleInstance(gameHandle);
    }
}
