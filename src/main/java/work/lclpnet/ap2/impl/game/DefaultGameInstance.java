package work.lclpnet.ap2.impl.game;

import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.lobby.game.util.ProtectorUtils;

public abstract class DefaultGameInstance implements MiniGameInstance {

    protected final MiniGameHandle gameHandle;

    public DefaultGameInstance(MiniGameHandle gameHandle) {
        this.gameHandle = gameHandle;
    }

    @Override
    public void start() {
        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        registerDefaultHooks();

        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        mapFacade.openRandomMap(gameId, this::onReady);
    }

    private void registerDefaultHooks() {
        gameHandle.getHookRegistrar().registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (!source.isOf(DamageTypes.OUT_OF_WORLD) || !(entity instanceof ServerPlayerEntity player)) return true;

            if (player.isSpectator()) {
                gameHandle.getWorldFacade().teleport(player);
                return false;
            }

            return true;
        });
    }

    protected abstract void onReady();
}
