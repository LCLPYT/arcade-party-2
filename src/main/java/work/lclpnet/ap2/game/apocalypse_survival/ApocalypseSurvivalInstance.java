package work.lclpnet.ap2.game.apocalypse_survival;

import net.minecraft.entity.damage.DamageTypes;
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

        var setup = new AsSetup(getMap(), getWorld(), new Random());
        spawners = setup.readSpawners();
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> !source.isOf(DamageTypes.PLAYER_ATTACK));
            config.allow(ProtectionTypes.MOB_GRIEFING, ProtectionTypes.EXPLOSION);
        });

        gameHandle.getGameScheduler().interval(this::tick, 1);
    }

    private void tick() {
        spawners.forEach(MonsterSpawner::tick);
    }
}
