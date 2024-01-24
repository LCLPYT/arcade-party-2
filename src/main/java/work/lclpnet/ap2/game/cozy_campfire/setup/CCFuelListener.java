package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.team.Team;

public interface CCFuelListener {

    void onAddFuel(ServerPlayerEntity player, BlockPos pos, Team team, ItemStack stack);
}
