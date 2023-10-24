package work.lclpnet.ap2.impl.base.prot;

import net.minecraft.util.ActionResult;
import work.lclpnet.ap2.api.base.prot.ProtectionConfig;
import work.lclpnet.ap2.api.base.prot.ProtectionScope;
import work.lclpnet.ap2.api.base.prot.ProtectionType;
import work.lclpnet.ap2.api.base.prot.Protector;
import work.lclpnet.kibu.hook.player.PlayerFoodHooks;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.kibu.hook.world.WorldPhysicsHooks;
import work.lclpnet.kibu.plugin.hook.HookContainer;
import work.lclpnet.mplugins.ext.Unloadable;

import java.util.function.Consumer;

import static work.lclpnet.ap2.api.base.prot.ProtectionType.*;

public class ConfigurableProtector implements Protector, Unloadable {

    private final ProtectionConfig config;
    private final HookContainer hooks;

    public ConfigurableProtector(ProtectionConfig config) {
        this.config = config;
        this.hooks = new HookContainer();
    }

    public void activate() {
        protect(BREAK_BLOCKS, scope -> BlockModificationHooks.BREAK_BLOCK.register(noModification(scope)));

        protect(PLACE_BLOCKS, scope -> BlockModificationHooks.PLACE_BLOCK.register((world, pos, entity, newState)
                -> scope.isWithinScope(entity)));

        protect(PICKUP_FLUID, scope -> BlockModificationHooks.PICKUP_FLUID.register((world, pos, entity, fluid)
                -> scope.isWithinScope(entity)));

        protect(PLACE_FLUID, scope -> BlockModificationHooks.PLACE_FLUID.register((world, pos, entity, fluid)
                -> scope.isWithinScope(entity)));

        protect(USE_ITEM_ON_BLOCK, scope -> BlockModificationHooks.USE_ITEM_ON_BLOCK.register((ctx)
                -> scope.isWithinScope(ctx.getPlayer()) ? ActionResult.FAIL : null));

        protect(TRAMPLE_FARMLAND, scope -> BlockModificationHooks.TRAMPLE_FARMLAND.register((world, pos, entity)
                -> scope.isWithinScope(entity)));

        protect(HUNGER, scope -> {
            PlayerFoodHooks.LEVEL_CHANGE.register((player, fromLevel, toLevel)
                    -> scope.isWithinScope(player));
            PlayerFoodHooks.EXHAUSTION_CHANGE.register((player, fromLevel, toLevel)
                    -> scope.isWithinScope(player));
            PlayerFoodHooks.SATURATION_CHANGE.register((player, fromLevel, toLevel)
                    -> scope.isWithinScope(player));
        });

        // TODO introduce scopes with different arguments then entity, such as block pos or world
//        protect(MOB_GRIEFING, scope -> BlockModificationHooks.CAN_MOB_GRIEF.register((world, pos, entity)
//                -> scope.isWithinScope(world)));

        protect(CHARGE_RESPAWN_ANCHOR, scope -> BlockModificationHooks.CHARGE_RESPAWN_ANCHOR.register(noModification(scope)));

        protect(COMPOSTER, scope -> BlockModificationHooks.COMPOSTER.register(noModification(scope)));

        protect(EAT_CAKE, scope -> BlockModificationHooks.EAT_CAKE.register(noModification(scope)));

        protect(EXPLODE_RESPAWN_LOCATION, scope -> BlockModificationHooks.EXPLODE_RESPAWN_LOCATION.register(noModification(scope)));

        protect(EXTINGUISH_CANDLE, scope -> BlockModificationHooks.EXTINGUISH_CANDLE.register(noModification(scope)));

        protect(PRIME_TNT, scope -> BlockModificationHooks.PRIME_TNT.register(noModification(scope)));

        protect(TAKE_LECTERN_BOOK, scope -> BlockModificationHooks.TAKE_LECTERN_BOOK.register(noModification(scope)));

        protect(TRAMPLE_TURTLE_EGG, scope -> BlockModificationHooks.TRAMPLE_TURTLE_EGG.register(noModification(scope)));

//        protect(CAULDRON_DRIP_STONE, scope -> WorldPhysicsHooks.CAULDRON_DRIP_STONE.register((world, pos, newState)
//                -> scope.isWithinScope(world, pos)));
    }

    private BlockModificationHooks.BlockModifyHook noModification(ProtectionScope scope) {
        return (world, pos, entity) -> scope.isWithinScope(entity);
    }

    @Override
    public void unload() {
        hooks.unload();
    }

    private void protect(ProtectionType type, Consumer<ProtectionScope> setupAction) {
        if (!config.hasRestrictions(type)) return;

        ProtectionScope allowed = config.getAllowedScope(type);
        ProtectionScope disallowed = config.getDisallowedScope(type);

        // disallowed and not explicitly allowed
        setupAction.accept(ProtectionScope.minus(disallowed, allowed));
    }
}
