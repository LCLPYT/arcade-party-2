package work.lclpnet.ap2.game.panda_finder;


import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PandaManager {

    private static final int PANDA_COUNT = 100;
    private final List<Vec3d> spawns;
    private final Random random;
    private final ServerWorld world;
    private final Set<PandaEntity> pandas = new HashSet<>();
    private PandaEntity.Gene current;

    public PandaManager(List<Vec3d> spawns, Random random, ServerWorld world) {
        if (spawns.isEmpty()) throw new IllegalArgumentException("Spawn list is empty");

        this.spawns = spawns;
        this.random = random;
        this.world = world;
    }

    public void next() {
        PandaEntity.Gene[] genes = PandaEntity.Gene.values();
        current = genes[random.nextInt(genes.length)];

        clear();

        populate();
    }

    private void populate() {
        PandaEntity.Gene[] otherGenes = Arrays.stream(PandaEntity.Gene.values())
                .filter(gene -> gene != current)
                .toArray(PandaEntity.Gene[]::new);

        int index = random.nextInt(PANDA_COUNT);

        for (int i = 0; i < PANDA_COUNT; i++) {
            Vec3d pos = randomPosition();

            PandaEntity panda = new PandaEntity(EntityType.PANDA, world);

            pandas.add(panda);

            PandaEntity.Gene gene = i == index ? current : otherGenes[random.nextInt(otherGenes.length)];
            panda.setMainGene(gene);
            panda.setHiddenGene(gene);

            panda.setBaby(random.nextFloat() < 0.05);

            panda.setPosition(pos);

            world.spawnEntity(panda);
        }
    }

    private Vec3d randomPosition() {
        return spawns.get(random.nextInt(spawns.size()));
    }

    private void clear() {
        for (PandaEntity panda : pandas) {
            panda.discard();
        }
    }
}
