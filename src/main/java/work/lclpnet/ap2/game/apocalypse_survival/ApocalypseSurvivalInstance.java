package work.lclpnet.ap2.game.apocalypse_survival;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.apocalypse_survival.util.AsSetup;
import work.lclpnet.ap2.game.apocalypse_survival.util.MonsterSpawner;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.List;
import java.util.Random;

public class ApocalypseSurvivalInstance extends EliminationGameInstance {

    private List<MonsterSpawner> spawners;

    public ApocalypseSurvivalInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected void prepare() {
        useTaskDisplay();
        useNoHealing();
        useSmoothDeath();

        ServerWorld world = getWorld();
        MinecraftServer server = gameHandle.getServer();

        var setup = new AsSetup(getMap(), world, new Random());
        spawners = setup.readSpawners();

        GameRules gameRules = world.getGameRules();
        gameRules.get(GameRules.FALL_DAMAGE).set(true, server);
        gameRules.get(GameRules.DO_MOB_GRIEFING).set(true, server);
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.ALLOW_DAMAGE, this::allowDamage);
            config.allow(ProtectionTypes.MOB_GRIEFING, ProtectionTypes.EXPLOSION);
        });

        gameHandle.getGameScheduler().interval(this::tick, 1);
    }

    private boolean allowDamage(Entity entity, DamageSource source) {
        if (source.isOf(DamageTypes.FALL) && entity instanceof MobEntity) {
            return false;
        }

        return !source.isOf(DamageTypes.PLAYER_ATTACK);
    }

    private void tick() {
        spawners.forEach(MonsterSpawner::tick);
    }
}
