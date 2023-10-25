package work.lclpnet.ap2.api.base.prot;

import com.google.common.collect.ImmutableSet;
import work.lclpnet.ap2.api.base.prot.scope.EntityBlockScope;
import work.lclpnet.ap2.api.base.prot.scope.EntityScope;
import work.lclpnet.ap2.api.base.prot.scope.PlayerScope;
import work.lclpnet.ap2.api.base.prot.scope.WorldBlockScope;
import work.lclpnet.ap2.api.base.prot.type.EntityBlockProtectionType;
import work.lclpnet.ap2.api.base.prot.type.EntityProtectionType;
import work.lclpnet.ap2.api.base.prot.type.PlayerProtectionType;
import work.lclpnet.ap2.api.base.prot.type.WorldBlockProtectionType;

import java.util.Set;

public class BuiltinProtectionTypes {

    public static final ProtectionType<EntityBlockScope> BREAK_BLOCKS;
    public static final ProtectionType<EntityBlockScope> PLACE_BLOCKS;
    public static final ProtectionType<EntityBlockScope> PICKUP_FLUID;
    public static final ProtectionType<EntityBlockScope> PLACE_FLUID;
    public static final ProtectionType<EntityBlockScope> USE_ITEM_ON_BLOCK;
    public static final ProtectionType<EntityBlockScope> TRAMPLE_FARMLAND;
    public static final ProtectionType<PlayerScope> HUNGER;
    public static final ProtectionType<EntityBlockScope> MOB_GRIEFING;
    public static final ProtectionType<EntityBlockScope> CHARGE_RESPAWN_ANCHOR;
    public static final ProtectionType<EntityBlockScope> COMPOSTER;
    public static final ProtectionType<EntityBlockScope> EAT_CAKE;
    public static final ProtectionType<EntityBlockScope> EXPLODE_RESPAWN_LOCATION;
    public static final ProtectionType<EntityBlockScope> EXTINGUISH_CANDLE;
    public static final ProtectionType<EntityBlockScope> PRIME_TNT;
    public static final ProtectionType<EntityBlockScope> TAKE_LECTERN_BOOK;
    public static final ProtectionType<EntityBlockScope> TRAMPLE_TURTLE_EGG;
    public static final ProtectionType<WorldBlockScope> CAULDRON_DRIP_STONE;
    public static final ProtectionType<EntityBlockScope> EXPLOSION;
    public static final ProtectionType<WorldBlockScope> MELT;
    public static final ProtectionType<WorldBlockScope> FREEZE;
    public static final ProtectionType<WorldBlockScope> SNOW_FALL;
    public static final ProtectionType<WorldBlockScope> CAULDRON_PRECIPITATION;
    public static final ProtectionType<EntityBlockScope> FROST_WALKER_FREEZE;
    public static final ProtectionType<PlayerScope> DROP_ITEM;
    public static final ProtectionType<EntityScope> ALLOW_DAMAGE;

    private static final Set<ProtectionType<?>> types;

    static {
        types = ImmutableSet.<ProtectionType<?>>builder()
                .add(BREAK_BLOCKS = new EntityBlockProtectionType())
                .add(PLACE_BLOCKS = new EntityBlockProtectionType())
                .add(PICKUP_FLUID = new EntityBlockProtectionType())
                .add(PLACE_FLUID = new EntityBlockProtectionType())
                .add(USE_ITEM_ON_BLOCK = new EntityBlockProtectionType())
                .add(TRAMPLE_FARMLAND = new EntityBlockProtectionType())
                .add(HUNGER = new PlayerProtectionType())
                .add(MOB_GRIEFING = new EntityBlockProtectionType())
                .add(CHARGE_RESPAWN_ANCHOR = new EntityBlockProtectionType())
                .add(COMPOSTER = new EntityBlockProtectionType())
                .add(EAT_CAKE = new EntityBlockProtectionType())
                .add(EXPLODE_RESPAWN_LOCATION = new EntityBlockProtectionType())
                .add(EXTINGUISH_CANDLE = new EntityBlockProtectionType())
                .add(PRIME_TNT = new EntityBlockProtectionType())
                .add(TAKE_LECTERN_BOOK = new EntityBlockProtectionType())
                .add(TRAMPLE_TURTLE_EGG = new EntityBlockProtectionType())
                .add(CAULDRON_DRIP_STONE = new WorldBlockProtectionType())
                .add(EXPLOSION = new EntityBlockProtectionType())
                .add(MELT = new WorldBlockProtectionType())
                .add(FREEZE = new WorldBlockProtectionType())
                .add(SNOW_FALL = new WorldBlockProtectionType())
                .add(CAULDRON_PRECIPITATION = new WorldBlockProtectionType())
                .add(FROST_WALKER_FREEZE = new EntityBlockProtectionType())
                .add(DROP_ITEM = new PlayerProtectionType())
                .add(ALLOW_DAMAGE = new EntityProtectionType())
                .build();
    }

    private BuiltinProtectionTypes() {}

    public static Set<ProtectionType<?>> getTypes() {
        return types;
    }
}
