package work.lclpnet.ap2.core.hook;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface ChiseledBookshelfModifyCallback {

    Hook<ChiseledBookshelfModifyCallback> ADD = HookFactory.createArrayBacked(ChiseledBookshelfModifyCallback.class, callbacks -> (player, pos) -> {
        for (var cb : callbacks) {
            cb.onModifyBook(player, pos);
        }
    });

    Hook<ChiseledBookshelfModifyCallback> REMOVE = HookFactory.createArrayBacked(ChiseledBookshelfModifyCallback.class, callbacks -> (player, pos) -> {
        for (var cb : callbacks) {
            cb.onModifyBook(player, pos);
        }
    });

    void onModifyBook(ServerPlayerEntity player, BlockPos pos);
}
