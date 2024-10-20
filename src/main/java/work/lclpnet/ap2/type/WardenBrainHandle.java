package work.lclpnet.ap2.type;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.mob.WardenEntity;

public interface WardenBrainHandle extends BrainHandle<WardenEntity> {

    void addCoreActivities(Brain<WardenEntity> brain);

    void addIdleActivities(Brain<WardenEntity> brain);

    void addRoarActivities(Brain<WardenEntity> brain);

    void addInvestigateActivities(Brain<WardenEntity> brain);

    void addSniffActivities(Brain<WardenEntity> brain);
}
